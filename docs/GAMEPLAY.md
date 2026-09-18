# Gameplay and controls

[Project overview](../README.md) · [Development](DEVELOPMENT.md) · [Design rationale](DESIGN.md)

## Gameplay

The paddle follows your mouse, and only your mouse. It slides around on one flat plane at bat
height: move the mouse **across** and the paddle goes across, move it **up the screen** and the
paddle moves up the table toward the net, **down** and it comes back behind the baseline for a
deep ball. In normal play the mouse keeps the bat at one height and moves it around
the table, which is what keeps "reach in" and "step back" from fighting each other.

How you move it through the ball aims the shot: **swipe it sideways** to send the ball that way
(swipe right, ball goes right), **drive it up-table through the ball** for pace, depth and
topspin — the face closes over the ball as you go forward — and **pull it back** through the ball
to open the face and cut backspin under it. A still paddle just blocks it back soft. **Hold the
right mouse button** and the up/down axis stops moving the paddle up the table and starts raising
and lowering the bat instead, so you can brush up or down the back of the ball; depth freezes while
you hold it. The game then
keeps that shot playable — the ball is aimed at a real spot on the other side, and the shot is
checked all the way to the bounce before it is played, so it clears the net and lands in rather
than flying off the end. It is assisted, arcade-style, more *Ping Pong Fury* than a physics sim
(though a full physics simulation runs underneath — see below).

What that buys, measured over 75 different ways of swinging the paddle at the same ball: the raw
physics puts 11 of them on the table and throws the ball up to 2.4 m wide at 24 m/s; through the
assist all 75 land, none more than 0.38 m off centre, none faster than 12.4 m/s. Hitting harder
always does a little more and never a lot more, so a long rally cannot spiral into a rocket.

The assist is not a guarantee, though — **you can still miss.** Every contact is graded on where it
struck the blade, and the further out toward the rim you catch the ball the more of the raw physics
you get and the less of the aimed shot. A clean, centred contact goes where you aimed it; a shot off
the edge mostly does what real physics says, which is usually to die. Against a 5 m/s ball you have
about 0.6 of the blade's radius to play with, and about 0.3 of it against an 18 m/s one.

You can only return the ball **after it has bounced on your side** (real table-tennis rule). A ball
that bounces twice, comes back on your own half, or sails past the end line ends the point, and the
next serve is fed in. Clipping the net is *not* an automatic loss — as in the real game, a ball that
touches the cord and still lands in is a good shot.

The match is scored to the real rules: **games to 11, win by two**, with no ceiling at deuce, so
13–11 and 24–22 both finish a game. Service changes every two points, and every single point once
the score reaches 10–all. A match is the best of five.

The camera is your main tool for reading the ball: it cuts between a close view and a wide one
depending on who last hit — close after your shot, wide after the opponent's.

Under the arcade assist there is a full physics simulation: in flight the ball is simulated rather
than scripted — spin curves it in the air, and it changes the bounce when the ball lands (heavy
topspin kicks forward, backspin sits up). The assist shapes the ball only at the moment a racket
hits it; between hits it flies for real.

Press **`S`** to see that underlying simulation directly: it turns the shot assist off for both
rackets, so every contact keeps the raw impulse-solver bounce instead of an authored shot aimed
into the target box. This is genuinely harder, not just less forgiving — measured over the same
75-swing sweep used to tune the assist, only 11 of 75 land on the table at all, and the rest go up
to 2.4 m wide at 24 m/s. `S` again turns the assist back on.

## Features

**Playable now**

- Mouse-controlled paddle that slides anywhere from over the table to well behind the baseline,
  wherever YOU point it
- Assisted arcade shots — drive for pace, swipe to aim; the game keeps the ball in play
- Rallies against the AI, with the one-bounce rule and points that end on a long ball or a double bounce
- A match scored to the real rules — games to 11, win by two, service changing on the right points
- Shots you can genuinely miss: catch the ball off the rim of the bat and real physics takes over
- A two-view rally-cam that cuts on who last hit, plus five preset views, slow motion, single-step
- Full ball-flight simulation between hits: spin, air drag, Magnus curve, spin-coupled bounces
- A menu of shots to feed in, a grey no-spin "ghost" trail, and a `V` debug overlay that shows
  exactly how a shot was chosen
- A raw-physics mode (`S`) that turns the shot assist off entirely, for players who want the real
  simulation with no aim help on either racket

**In progress**

- Serving off your own blade (right now a ball is fed in each rally)
- An AI opponent that reads where the ball is going rather than tracking it
- A menu, so the game is something you start rather than something you launch

**Planned**

- Multiple paddles that play differently — one built for spin, one for power
- Earn currency by beating the AI and spend it in a shop

## Controls

Playing the ball:

| Input | Action |
| --- | --- |
| Mouse left / right | Moves the paddle across the table |
| Mouse up / down | Moves the paddle up the table toward the net, or back behind the baseline |
| Hold right mouse | Switches up / down to raising and lowering the bat, for brushing up or down the ball. Depth is frozen while held |
| Swipe the paddle sideways | Sends the ball that way — swipe right, ball goes right |
| Drive the paddle up-table through the ball | Pace, depth and topspin |
| Pull the paddle back through the ball | Opens the face and cuts backspin |
| Still paddle | Soft block back |

There is no hit button — the shot is entirely in the mouse movement. Normally the paddle stays at
one height; hold the right mouse button to switch the vertical cursor axis to brushing.

Other keys (carried over from the physics demo):

| Key | Action |
| --- | --- |
| `1`–`9`, `0` | Pick the feed shot |
| `N` / `P`, `←` `→` | Next / previous feed |
| `R` | Replay the current feed |
| `Space` | Pause |
| `.` | Single physics step |
| `[` `]` | Slow down / speed up (starts at 0.45×) |
| `F` | Rally-cam on / off |
| `C` | Cycle the preset camera views (turns the rally-cam off) |
| `V` | Shot-assist debug overlay (paddle and ball velocity, raw / intended / final shot, target, predicted landing) |
| `D` | Control debug overlay (cursor, paddle and target position, legal paddle area, how far and how long the paddle has to travel, where the ball is and when it arrives, and whether you could have got there) |
| `G` | Toggle the no-spin ghost trail |
| `T` | Toggle the flight trail |
| `B` | Draw the ball at 2× (physics still uses 40 mm) |
| `A` | Toggle auto-replay |
| `S` | Toggle raw-physics mode: no shot assist, either racket -- real physics only |
| `H` | Toggle the on-screen legend |
| `Esc` | Quit |
| Left-drag / scroll | Orbit / zoom the camera (turns the follow-cam off) |
