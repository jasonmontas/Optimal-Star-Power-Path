# Introduction/Goals

Our goal for this project is to create an algorithm that finds the
optimal star power path for any chart in the game *Clone Hero* to
achieve a high score. We will create a website that serves as a GUI for this algorithm.

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

In *Clone Hero* there are two types of notes. Standard notes give 50 points when hit. Sustain notes are similar to standard notes,
but holding down the note gives more points per bar held. As you hit
more notes without missing, your score multiplier meter will increase,
with a max of a 4x multiplier.

In the game there is something called star power, which doubles the
score multipler when activated. In order to obtain star power, there are
sections called star power phrases. When you hit all notes in the star
power phrase, the meter will increase by 25%. When the meter hits 50%,
you can then activate star power.

Generally speaking, the best time to activate star power is when there
is a section packed with notes, so that you can maximize the usefulness
of it.

# How the algorithm will function

The algorithm currently works as a command-line pipeline. The
user gives the program an input chart file and an output image
path. The input can be either a .chart file or a .mid file.
First, the parser reads the file and converts it into a shared
internal chart model. This model contains note timing, sustain
lengths, star power phrase ranges, tempo events, time
signatures, and chart resolution.

Next, the optimizer evaluates possible star power activation
decisions across the song and computes the activation path that
gives the highest total score under the implemented game rules.
Finally, the renderer generates a PNG image of the full chart
and highlights the recommended activation points so the result
is easy to follow visually.

# Tools

We are using Java for the majority of this project. We will use
HTML, CSS, and JavaScript to create a website that will serve
as a GUI for input and output

# TimeLine

Tweak algorithm to be more accurate and include star power gained from sustains

Make a GUI for project

Add a feature that notifies player when to activate star power in real time

# Diagram

![System Diagram](diagram.png)


