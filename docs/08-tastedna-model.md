# 08 - TasteDNA Model

## Overview
TasteDNA is a 10-dimensional preference vector representing a user's affinity across core media attributes.

## Vector Structure
The array follows the exact index mapping of the StoryPrint model:
`[Pacing, Emotional Weight, Complexity, Atmosphere, Tone, Action, Thematic Depth, Humor, Tension, Character Focus]`

## Learning Mechanism
* Default initial vector: `[50, 50, 50, 50, 50, 50, 50, 50, 50, 50]`
* When a user rates a movie `r` (scale 1 to 5), the system computes a weight modifier:
  `w = (r - 3) * 0.1`
* Each dimension `i` of TasteDNA is updated towards the movie's StoryPrint dimension `S[i]` proportionally.