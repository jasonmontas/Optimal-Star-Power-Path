# Week starting 2/3

- Researched algorithms to implement for optimal star power path/usage
- After looking at some algorithms including shortest path and greedy algorithm,dynamic programming seemed to be the best way to go.
    *    Using DP, the chart will be broken up into subproblems at each potential spot you can activate star power.
    *    From there the maximum score achievable will be calculated from that point onward.
    *    This will help with considerations for when it would be beneficial to not use star power as soon as the meter hits 50%, and other scenarios where you would want to hold off on activating