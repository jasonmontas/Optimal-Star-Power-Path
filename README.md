# Introduction/Goals

My goal for this project is to create an algorithm that finds the
optimal star power path for any chart in the game *Clone Hero* to
achieve a high score; however, I would like to alter it to also apply to
older *Guitar Hero* games in which star power works differently. I will
either make a software that incorporates this algorithm, or a website
that will serve the same purpose.

# Background

## What is *Clone Hero*?

*Clone Hero* is a rhythm game based on the popular *Guitar Hero* series.
The gameplay is as follows: notes will appear on what is referred to as
a highway, and you hit the notes by pressing/strumming the corresponding
buttons using a plastic guitar controller.

*Clone Hero* is a community driven game, with people creating charts for
the game, while *Guitar Hero* works like any other game. You buy the
game, and you have a limited number of songs that come with the game.

## How scoring in *Clone Hero* works

In *Clone Hero* there are two types of notes. Standard notes give x
amount of points when hit. Sustain notes are similar to standard notes,
but holding down the note gives more points per bar held. As you hit
more notes without missing, your score multiplier meter will increase,
with a max of a 4x multiplier.

In the game there is something called star power, which doubles the
score multipler when activated. In order to obtain star power, there are
sections called star power phrases. When you hit all notes in the star
power phrase, the meter will increase by 25%. When the meter hits 50%,
you can then activate star power. In *Clone Hero* you can hit star power
phrases while star power is activated to lengthen your time, but in
older *Guitar Hero* games this feature isn't available.

Generally speaking, the best time to activate star power is when there
is a section packed with notes, so that you can maximize the usefulness
of it.

# How the algorithm will function

The algorithm will work as follows. The user will upload the folder all
song data is stored in. The algorithm will read either the .mid or
.chart file, and extract where all star power phrases in the song are
located. It will then calculate when the best time to activate is, and
will provide an output telling the user when to activate. I'd like the
output to be a picture of the entire chart, with highlights on the
sections that star power should be activated in.

# Tools

I will most likely use Java to write the algorithm, since it is the
language I'm most proficient in. I will then most likely use HTML, CSS,
and JavaScript to create a website that will serve as a GUI for input
and output.

# TimeLine

Now: CLI  tool that takes notes.chart file summarizes and outputs a PNG image that represents when the notes come up.

Later: Convert chart into an internal chart model with notes phrases and star power data then right a basic optimizer (More of a skeleton).

Later 2: Implement actual game rules into code to make an optimizer that outputs a star power activation plan.

Later 3: Add Guitar Hero rules and website compatibility by swapping IO rules modules.
