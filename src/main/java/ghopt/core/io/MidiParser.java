package ghopt.core.io;

import ghopt.core.model.ChartData;
import java.io.File;
import javax.sound.midi.*;

public class MidiParser implements ChartSource {

    @Override
    public ChartData parse(File file) throws Exception {
        ChartData data = new ChartData();

        Sequence sequence = MidiSystem.getSequence(file);
        data.setResolution(sequence.getResolution());
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();

                if (message instanceof ShortMessage sm) {
                    if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                        int midiNote = sm.getData1();
                        long tick = event.getTick();

                        int lane = midiNoteToLane(midiNote);
                        if (lane >= 0) {
                            data.addNote(new ghopt.core.model.Note(
                                    (int) tick,
                                    lane,
                                    0
                            ));
                        }
                    }
                }
            }
        }

        data.sortByTime();
        return data;
    }

    private int midiNoteToLane(int midiNote) {
        return switch (midiNote) {
            case 60 -> 0;
            case 61 -> 1;
            case 62 -> 2;
            case 63 -> 3;
            case 64 -> 4;
            default -> -1;
        };
    }
}