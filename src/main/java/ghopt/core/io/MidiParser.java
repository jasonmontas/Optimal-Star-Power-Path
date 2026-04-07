package ghopt.core.io;

import ghopt.core.model.*;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class MidiParser implements ChartSource {

    // MIDI files don't have named difficulty sections like .chart files,
    // so the difficulty parameter is ignored here — always parses Expert (PART GUITAR).
    @Override
    public ChartData parse(File file, String difficulty) throws IOException {

        ChartData chartData = new ChartData();

        try {

            Sequence sequence = MidiSystem.getSequence(file);
            chartData.setResolution(sequence.getResolution());

            Track[] tracks = sequence.getTracks();

            for (Track track : tracks) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);
                    MidiMessage message = event.getMessage();
                    if (message instanceof MetaMessage meta && meta.getType() == 0x58) {
                        byte[] data = meta.getData();
                        if (data.length >= 2) {
                            int numerator = data[0] & 0xFF;
                            int denominator = 1 << (data[1] & 0xFF);
                            chartData.addTimeSignatureEvent(new TimeSignatureEvent((int) event.getTick(), numerator, denominator));
                        }
                    }
                }
            }

            for (Track track : tracks) {

                String trackName = getTrackName(track);
                if (!isGuitarTrack(trackName)) continue;

                Map<Integer, Deque<Integer>> activeNotes = new HashMap<>();

                for (int i = 0; i < track.size(); i++) {

                    MidiEvent event = track.get(i);
                    MidiMessage message = event.getMessage();

                    if (message instanceof ShortMessage sm) {

                        int cmd      = sm.getCommand();
                        int note     = sm.getData1();
                        int velocity = sm.getData2();
                        int tick     = (int) event.getTick();

                        boolean noteOn  = cmd == ShortMessage.NOTE_ON && velocity > 0;
                        boolean noteOff = cmd == ShortMessage.NOTE_OFF ||
                                (cmd == ShortMessage.NOTE_ON && velocity == 0);

                        if (noteOn) {
                            activeNotes
                                .computeIfAbsent(note, k -> new ArrayDeque<>())
                                .addLast(tick);
                        } else if (noteOff) {
                            Deque<Integer> starts = activeNotes.get(note);
                            if (starts != null && !starts.isEmpty()) {
                                int start    = starts.removeFirst();
                                int duration = Math.max(0, tick - start);
                                parseMidiNote(chartData, note, start, duration);
                                if (starts.isEmpty()) activeNotes.remove(note);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new IOException("Failed to parse MIDI", e);
        }

        chartData.sortByTime();
        if (chartData.getTimeSignatureEvents().isEmpty()) {
            chartData.addTimeSignatureEvent(new TimeSignatureEvent(0, 4, 4));
        } else if (chartData.getTimeSignatureEvents().get(0).getTick() != 0) {
            chartData.getTimeSignatureEvents().add(0, new TimeSignatureEvent(0, 4, 4));
        }
        return chartData;
    }

    private void parseMidiNote(ChartData chartData, int midiNote, int start, int duration) {
        if (midiNote >= 96 && midiNote <= 100) {
            int lane = midiNote - 96;
            chartData.addNote(new Note(start, lane, duration));
        } else if (midiNote == 116) {
            chartData.addStarPowerPhrase(new StarPowerPhrase(start, start + duration));
        } else if (midiNote == 106) {
            chartData.addNote(new Note(start, 7, duration));
        }
    }

    private boolean isGuitarTrack(String name) {
        if (name == null) return false;
        String n = name.trim().toUpperCase();
        return n.equals("PART GUITAR")
            || n.equals("PART GUITAR COOP")
            || n.equals("T1 GEMS");
    }

    private String getTrackName(Track track) {
        for (int i = 0; i < track.size(); i++) {
            MidiEvent event   = track.get(i);
            MidiMessage message = event.getMessage();
            if (message instanceof MetaMessage meta) {
                if (meta.getType() == 0x03)
                    return new String(meta.getData()).trim();
            }
        }
        return "";
    }
}