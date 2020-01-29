# Mosaik
Casual game written in Java: Cleverly place various-shaped bricks to cover the board. Simple and compelling!

![Mosaik](https://repository-images.githubusercontent.com/237076242/88ece700-42f1-11ea-813e-90d8ab9189b0)

## Getting Started

No installation needed. Just clone or download the repository and run `javac Game.java` in the base directory. You need to have a Java Development Kit, version 8 or later, installed in order to use `javac`. I recommend OpenJDK.

After compiling, you can start Mosaik from the base directory using `java Game`.

## The Game

You are given a rectangular board of 15×10 tiles, and on the right hand side you can see some bricks in various shapes. Your aim is to place as many bricks on the board as you can.

Click on a brick to pick it up. Now you have to place it, you may not put it back again. Use the mousewheel or Ctrl-Click to rotate the brick you're holding. Click to place it in a free location on the board. A new brick will appear on the right.

In the bottom-right corner, you can see your score. You gain points for every brick placed – the bigger the brick, the more points you get for it.

When you have filled the entire board with bricks, you'll get another board straight away to gain even more points.

When you have picked uo a bricks which you can't or don't want to place, you can remove it by clicking on the bin region next to the points display. Points will be deducted when you remove bricks. On top of the bin region is the number of bricks you may still remove. It will be decreased for every brick you remove, and increased by 1 every time you complete a board.

When you cannot place any more bricks and the bin region is saturated, the game is over. You might have earned a place in the highscore!

## The Menu

Press Escape to open the menu. The game will be saved automatically. (This is also the case when closing Mosaik during a game.)

In the menu, you can continue the saved game (if any), or start a new game with any desired board size and brickset. A brickset is the set of all brick shapes that will appear during the game.

Use the Up/Down arrow keys to navigate the menu, and the Left/Right keys to change the values for board size and brickset. Use Enter to select the highlighted value. Use Escape to quit.

## Custom Data

All data is stored in the `data` directory. Place your own images in `data/images` to include them as backgrounds in the game.

### How to design your own bricksets

Bricksets are defined in `data/bricksets`. A brickset file contains the following commands:
- `new b` - defines a new brick with the internal name `b`
- `col b rrggbb` - sets the color of brick `b` to the given hex color value.
- `add b x y` - defines that brick `b` covers the tile located `x` units east and `y` units south of its center

## Website

[Repository](https://github.com/Noordfrees/Mosaik)

[Bug reports go here](https://github.com/Noordfrees/Mosaik/issues)

## Credits

Created by [Benedikt Straub (@Noordfrees)](https://github.com/Noordfrees) in his spare time. I hope you like my work.

## License

GPL3. See the file [LICENSE](https://github.com/Noordfrees/Mosaik/blob/master/LICENSE) for details.
