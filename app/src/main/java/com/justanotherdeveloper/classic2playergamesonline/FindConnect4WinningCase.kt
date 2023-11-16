package com.justanotherdeveloper.classic2playergamesonline

import stanford.androidlib.graphics.GOval
import java.util.*

/** Code snippet file - source: Connect4Canvas.kt (line 903)
 *
 * pieceConnectFour() method is called after each players turn and uses
 * depth-first-search algorithm to determine if the last move wins the game
 */

private lateinit var slots: Array<Array<Connect4Canvas.Slot>>
private val connectedPieces = Stack<Connect4Canvas.Slot>()

private fun pieceConnectedFour(row: Int, col: Int, moveWasRed: Boolean): Boolean {
    connectedPieces.push(slots[row][col])
    val verticalCase =
        1 + pieceCounter(row - 1, col, 1, moveWasRed, "north") +
                pieceCounter(row + 1, col, 1, moveWasRed, "south")
    if(verticalCase >= 4) return true
    else while(connectedPieces.size > 1) connectedPieces.pop()

    val horizontalCase =
        1 + pieceCounter(row, col + 1, 1, moveWasRed, "east") +
                pieceCounter(row, col - 1, 1, moveWasRed, "west")
    if(horizontalCase >= 4) return true
    else while(connectedPieces.size > 1) connectedPieces.pop()

    val inclineCase =
        1 + pieceCounter(row - 1, col + 1, 1, moveWasRed, "northeast") +
                pieceCounter(row + 1, col - 1, 1, moveWasRed, "southwest")
    if(inclineCase >= 4) return true
    else while(connectedPieces.size > 1) connectedPieces.pop()

    val declineCase =
        1 + pieceCounter(row - 1, col - 1, 1, moveWasRed, "northwest") +
                pieceCounter(row + 1, col + 1, 1, moveWasRed, "southeast")
    if(declineCase >= 4) return true
    else while(connectedPieces.size > 0) connectedPieces.pop()
    return false
}

private fun pieceCounter(row: Int, col: Int, count: Int, moveWasRed: Boolean, direction: String): Int {
    if (slotIsOutOfBounds(row, col) || slots[row][col].isEmpty || slots[row][col].isRedPiece != moveWasRed)
        return count - 1
    connectedPieces.push(slots[row][col])
    return when (direction) {
        "north" -> pieceCounter(row - 1, col, count + 1, moveWasRed, direction)
        "south" -> pieceCounter(row + 1, col, count + 1, moveWasRed, direction)
        "east" -> pieceCounter(row, col + 1, count + 1, moveWasRed, direction)
        "west" -> pieceCounter(row, col - 1, count + 1, moveWasRed, direction)
        "northeast" -> pieceCounter(row - 1, col + 1, count + 1, moveWasRed, direction)
        "northwest" -> pieceCounter(row - 1, col - 1, count + 1, moveWasRed, direction)
        "southeast" -> pieceCounter(row + 1, col + 1, count + 1, moveWasRed, direction)
        else -> // southwest
            pieceCounter(row + 1, col - 1, count + 1, moveWasRed, direction)
    }
}

private fun slotIsOutOfBounds(row: Int, col: Int): Boolean {
    return row < 0 || row > connect4BoardRows-1 || col < 0 || col > connect4BoardCols-1
}

class Slot {
    var isEmpty = true
    var isRedPiece = false
    lateinit var slot: GOval
}