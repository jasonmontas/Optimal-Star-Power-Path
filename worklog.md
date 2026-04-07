# Week starting 2/3
- Researched algorithms to implement for optimal star power path/usage (JM)
- After looking at some algorithms including shortest path and greedy algorithm, dynamic programming seemed to be the best way to go. (JM)
    * Using DP, the chart will be broken up into subproblems at each potential spot you can activate star power.
    * From there the maximum score achievable will be calculated from that point onward.
    * This will help with considerations for when it would be beneficial to not use star power as soon as the meter hits 50%, and other scenarios where you would want to hold off on activating.

# Week starting 2/10
- Fixed up ChartParser.java (SH)
    * Improved parsing logic for .chart files
- Added .mid file support via MidiParser, with some bugs (JM)
- Implemented DP algorithm using parsed chart data (JM)
- Created a working bare bones CLI system that takes a notes.chart file, summarizes it, and outputs a PNG image representing when notes come up (SH)

# Week starting 2/17
- Fixed bugs involving .mid file parsing (JM)
- Made output of chart easier to read (JM)
    * Synced up notes and bar lines
    * Made green activation highlight bar more precise
- Introduced ChartSource abstraction to support multiple input formats (JM)
- Implemented MidiParser for basic .mid support (JM)
- Refactored Main to auto-detect input format (.chart or .mid) and parse accordingly (JM)

# Week starting 3/31
- Fixed combo counter in StarPowerOptimizer to increment by number of notes in a chord instead of always by 1, matching Clone Hero scoring rules (SH)
- Fixed sustain scoring to not multiply sustain points per chord note, since Clone Hero awards 25 pts per beat regardless of chord size (SH)
- Fixed activation time ordering in the DP solver to keep results in chronological order (SH)
- Added detailed comments to StarPowerOptimizer explaining Clone Hero scoring rules and SP meter math (SH)
- Added TempoEvent.java to store BPM change events from the [SyncTrack] section of .chart files (SH)
- Added TickConverter.java to convert tick positions to real timestamps, outputting results in m:ss.t format so activation points are meaningful to players (SH)
- Updated ChartData.java to store tempo events and song offset (SH)
- Updated ChartParser.java to read [SyncTrack] for BPM events, read the Offset field from [Song], and accept a difficulty parameter to support all four difficulty levels (SH)
- Updated ChartSource.java interface to include difficulty parameter in parse method (SH)
- Updated MidiParser.java to match new ChartSource interface (SH)
- Improved SP activation markers in ChartRenderer to be more visible with a thick gold line, transparent band, and ACTIVATE! label (SH)
- Added dark horizontal separator lines between chart rows in ChartRenderer to make rows easier to distinguish (SH)
- Built OptimizerGui.java, a full Swing GUI with file browser, difficulty picker, zoom controls, timestamp display, and chart image viewer (SH)
- Updated Main.java to pass Expert as default difficulty to match new ChartSource interface (SH)
