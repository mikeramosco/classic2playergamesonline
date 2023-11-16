package com.justanotherdeveloper.classic2playergamesonline

import android.content.Context
import android.graphics.*
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import stanford.androidlib.graphics.*
import java.util.*
import kotlin.collections.ArrayList

class Connect4Canvas(context: Context, attrs: AttributeSet)
    : GCanvas(context, attrs), View.OnTouchListener, GestureDetector.OnGestureListener {

    // Gesture Detector
    private lateinit var mGestureDetector: GestureDetector

    // Battleship Activity
    lateinit var linkedActivity: Connect4Activity

    // Host or Client Boolean
    var playerIsHost = false

    // Host Moves First Boolean
    var hostMovesFirst = false

    // Same Screen Option Boolean
    private var sameScreenOptionSelected = false

    // Messages List
    var messages = ArrayList<String>()

    // Current Message Displayed
    private var displayedMessageIndex = 0

    // for Online - true if phone holders turn
    // for Same Screen - true if player 1's turn
    private var isPlayersMove = true

    // true if player goes first - isRedPiece
    private var isPlayer1 = false

    override fun init() {
        mGestureDetector = GestureDetector(linkedActivity, this)
        buildGame()
    }

    fun displayRoomCode(roomCode: String) {
        val roomCodeTitleLabel = GLabel("roomcode:")
        roomCodeTitleLabel.paint = getButtonTextPaint(Color.GRAY)
        roomCodeTitleLabel.fontSize = fontSize.toFloat()
        add(roomCodeTitleLabel, (width - roomCodeTitleLabel.width) / 2,
            (restartButton.y + buttonHeight + elementSpacing).toFloat())

        val roomCodeLabel = GLabel(roomCode)
        roomCodeLabel.paint = getButtonTextPaint(Color.GRAY)
        roomCodeLabel.fontSize = fontSize.toFloat() * 2
        add(roomCodeLabel, (width - roomCodeLabel.width) / 2,
            (roomCodeTitleLabel.y + roomCodeTitleLabel.height + elementSpacing).toFloat())
    }

    fun setupForSameScreenOption() {
        sameScreenOptionSelected = true
        gameIsReady = true
    }

    fun recalibrateMessageBar() {
        if(messages.size > 0) {
            displayedMessageIndex = messages.lastIndex
            nextMessageButton.bitmap = nextMessageButtonImages[1]
            if (messages.lastIndex > 0)
                prevMessageButton.bitmap = prevMessageButtonImages[0]
            else prevMessageButton.bitmap = prevMessageButtonImages[1]
        }
    }

    fun updateMessageBarText(text: String, isNewMessage: Boolean = true,
                             fromId: Int = gameMessageId) {
        if(text == "" && displayedMessageIndex > messages.lastIndex) return
        var message = text
        if(text != "") message += "\t$fromId"
        if (isNewMessage) {
            if(fromId == gameMessageId && text != "")
                linkedActivity.showReceivedMessage(text, fromId)
            newMessageAddedToDisplay(message)
        }
        var labelColor = ContextCompat.getColor(context, R.color.darkGray)
        if(text != "") {
            val messageContents = message.split("\t")
            val attachedId = messageContents[1].toInt()
            message = messageContents[0]
            when (attachedId) {
                gameMessageId -> { }
                linkedActivity.playerId -> {
                    message = "${linkedActivity.getString(R.string.youText)}: $message"
                    labelColor = ContextCompat.getColor(context, R.color.colorPrimary)
                }
                else -> {
                    message = "${linkedActivity.getString(R.string.themText)}: $message"
                    labelColor = ContextCompat.getColor(context, R.color.colorPrimary)
                }
            }
        }
        updateDisplayedMessage(message, labelColor)
    }

    private fun newMessageAddedToDisplay(message: String) {
        if(message != "") messages.add(message)
        displayedMessageIndex = messages.lastIndex
        if(message == "") displayedMessageIndex++
        nextMessageButton.bitmap = nextMessageButtonImages[1]
        if (messages.lastIndex > 0 || message == "" && messages.size > 0)
            prevMessageButton.bitmap = prevMessageButtonImages[0]
        else prevMessageButton.bitmap = prevMessageButtonImages[1]
    }

    private fun updateDisplayedMessage(text: String, labelColor: Int) {
        var message = text
        messagesBar.remove(messagesBarLabel)
        var dotsAdded = false
        if (message.length > maxCharInMessage) {
            message = message.substring(0, maxCharInMessage) + "..."
            dotsAdded = true
        }
        messagesBarLabel.text = message
        messagesBarLabel.paint = getButtonTextPaint(labelColor)
        messagesBarLabel.fontSize = fontSize.toFloat()
        var count = 1
        var messageLength = message.length
        if (dotsAdded) messageLength -= 3
        while (messagesBarLabel.width > messageAreaWidth) {
            message = message.substring(0, messageLength - count++) + "..."
            messagesBarLabel.text = message
        }
        messagesBar.add(
            messagesBarLabel, (messagesBarWidth - messagesBarLabel.width).toFloat() / 2,
            (buttonHeight - messagesBarLabel.height).toFloat() / getNumberToDivide(message))
    }

    fun setPlayerWithFirstMove(hostMovesFirst: Boolean) {
        this.hostMovesFirst = hostMovesFirst
        if (playerIsHost == hostMovesFirst) {
            isPlayer1 = true
            updateMessageBarText(linkedActivity.getString(R.string.playersFirstTurnOnlinePrompt))
        } else {
            isPlayer1 = false
            linkedActivity.waitForOpponentsMove(true)
            updateMessageBarText(linkedActivity.getString(R.string.opponentFirstTurnOnlinePrompt))
        }
        gameIsReady = true
        isPlayersMove = isPlayer1
    }

    fun getOpponentsMove(boardState: String): Boolean {
        val boardStateContents = boardState.split("\t")
        val nBoardStateIds = connect4BoardRows * connect4BoardCols
        if(boardStateContents.size > nBoardStateIds+2) {
            if(boardStateContents[nBoardStateIds+1].toInt() != linkedActivity.playerId)
                return false
            var lastMove = boardStateContents.last()
            if(!lastMove.contains(":")) lastMove = boardStateContents[boardStateContents.lastIndex-1]
            lastMove = lastMove.split(":")[0]
            selectedCol = lastMove.toInt()
            addPiece(!isPlayer1, clickableSlots[selectedCol], true)
        } else return false
        return true
    }

    private fun resetGame() {
        isPlayersMove = true
        gameIsOver = false
        outOfSlots = false
        for(i in 0 until connect4BoardCols) nextEmptyRows[i] = connect4BoardRows-1
        val paint = Paint()
        paint.setARGB(0, 0, 0, 0)
        while(!connectedPieces.isEmpty()) {
            val piece = connectedPieces.pop()
            piece.slot.paint = paint
            piece.slot.sendToFront()
        }
        while(!redPieces.isEmpty()) {
            remove(redPieces.peek())
            redPieces.pop()
        }
        while(!yellowPieces.isEmpty()) {
            remove(yellowPieces.peek())
            yellowPieces.pop()
        }
        for((index, slot) in clickableSlots.withIndex()) {
            slot.moveTo((offsetSide + slotSpacing + index * (slotSize + slotSpacing)).toFloat(),
                (board.y - slotSpacing - slotSize).toFloat())
        }
        initSlots()
    }

    fun restartSameScreenGame() {
        resetGame()
        updateMessageBarText("")
    }

    fun restartOnlineGame() {
        resetGame()
        gameIsReady = false
        linkedActivity.displayAppropriateDialogForRestart()
    }

    // required override function to activate GestureDetector
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        mGestureDetector.onTouchEvent(motionEvent)
        return true
    }

    // Gets the element clicked on by user
    override fun onSingleTapUp(motionEvent: MotionEvent?): Boolean {
        if (motionEvent == null) return false
        if (sameScreenOptionSelected) runSameScreenFunctions(motionEvent)
        else runOnlineOptionFunctions(motionEvent)
        return false
    }

    private fun runSameScreenFunctions(motionEvent: MotionEvent) {
        if (gameIsReady && !actionInProgress && !animationInProgress) {
            checkIfButtonsClickedForSameScreenOption(motionEvent)
            checkIfPieceClicked(motionEvent)
            checkIfSlotClicked(motionEvent)
        }
    }

    private fun checkIfButtonsClickedForSameScreenOption(motionEvent: MotionEvent) {
        when (getElementAt(GPoint(motionEvent.x, motionEvent.y))) {
            menuButton -> {
                preventButtonClickUntilDialogAppears()
                linkedActivity.openMenu(false)
            }
            messagesButton -> {
                preventButtonClickUntilDialogAppears()
                linkedActivity.openMessagesForSameScreenOption(messages)
            }
            restartButton -> {
                preventButtonClickUntilDialogAppears()
                linkedActivity.sameScreenRestartClicked()
            }
            prevMessageButton -> displayPreviousMessage()
            nextMessageButton -> displayNextMessage()
        }
    }

    private fun checkIfPieceClicked(motionEvent: MotionEvent, forOnline: Boolean = false) {
        val isRedsTurn = if(forOnline) isPlayer1 == isPlayersMove else isPlayersMove
        if(yellowPieces.size > 0 &&
            getElementAt(GPoint(motionEvent.x, motionEvent.y)) == yellowPieces.peek() &&
            slotSelected && !isRedsTurn || redPieces.size > 0 &&
            getElementAt(GPoint(motionEvent.x, motionEvent.y)) == redPieces.peek() &&
            slotSelected && isRedsTurn) dropPiece(forOnline)
    }

    private fun countRemainingSlots() {
        outOfSlots = true
        for(nextEmptyRow in nextEmptyRows) if(nextEmptyRow >= 0) {
            outOfSlots = false
            break
        }
        if(outOfSlots) gameIsOver = true
    }

    private fun dropPiece(forOnline: Boolean, isOpponentsMove: Boolean = false) {
        val isRedPiece = if(forOnline) isPlayer1 == isPlayersMove else isPlayersMove
        val piece = getPieces(isRedPiece).last()
        selectedRow = nextEmptyRows[selectedCol]--
        if(nextEmptyRows[selectedCol] < 0)
            clickableSlots[selectedCol].moveTo(width.toFloat()*2, height.toFloat()*2)
        val slot = slots[selectedRow][selectedCol]
        slot.isEmpty = false
        slot.isRedPiece = isRedPiece
        var newY = piece.y
        val velocity = 20
        bringClickableBoardToFront()
        if(pieceConnectedFour(selectedRow, selectedCol, isRedPiece))
            gameIsOver = true
        if(!gameIsOver) countRemainingSlots()

        val message = if(forOnline) {
            if(!isOpponentsMove) {
                linkedActivity.updateBoardStateInFirebase(getBoardStateToUpdateFirebase())
                linkedActivity.waitForOpponentsMove()
            }
            if(!isPlayersMove) linkedActivity.getString(R.string.itsYourTurnText)
            else linkedActivity.getString(R.string.itsTheirTurnText)
        } else if(!isPlayersMove) linkedActivity.getString(R.string.itsRedsTurnText)
        else linkedActivity.getString(R.string.itsYellowsTurnText)
        if(!gameIsOver) updateMessageBarText(message)

        animationInProgress = true
        animate(connect4DropFPS) {
            if(newY < slot.slot.y) {
                newY += velocity
                piece.moveTo(piece.x, newY)

                if(newY >= slot.slot.y) {
                    piece.moveTo(piece.x, slot.slot.y - slotShiftingIndex)

                    slotSelected = false
                    animationInProgress = false
                    animationStop()
                    if(gameIsOver)
                        displayGameOverMessage(forOnline)
                    isPlayersMove = !isPlayersMove
                    logBoard()
                }
            }
        }
    }

    private fun displayGameOverMessage(forOnline: Boolean) {
        val message = if(outOfSlots) {
            linkedActivity.getString(R.string.gameOverOutOfSlots)
        } else if(forOnline) {
            if(isPlayersMove) linkedActivity.getString(R.string.onlineGameOverText,
                linkedActivity.getString(R.string.youText))
            else linkedActivity.getString(R.string.onlineGameOverText,
                linkedActivity.getString(R.string.theyText))
        } else if(isPlayersMove) linkedActivity.getString(R.string.gameOverRedWins)
        else linkedActivity.getString(R.string.gameOverYellowWins)
        updateMessageBarText(message)
        if(!outOfSlots) highlightWinningFourPieces()
    }

    private fun highlightWinningFourPieces() {
        while(connectedPieces.size > 4) connectedPieces.pop()
        val isRedPiece = connectedPieces.peek().isRedPiece

        val r = if(isRedPiece) winRedR else winYellowR
        val g = if(isRedPiece) winRedG else winYellowG
        val b = if(isRedPiece) winRedB else winYellowB

        val paint = Paint()
        paint.setARGB(255, r, g, b)

        for(piece in connectedPieces) piece.slot.paint = paint
        board.sendToFront()
    }

    private fun logBoard() {
        var str = "board:\n"
        for(row in 0 until connect4BoardRows) {
            for(col in 0 until connect4BoardCols) {
                val slot = slots[row][col]
                str += when {
                    slot.isEmpty -> "O "
                    slot.isRedPiece -> "R "
                    else -> "Y "
                }
            }
            str += "\n"
        }
//        Log.d("dtag:board", str)
    }

    private fun getBoardStateToUpdateFirebase(): String {
        var boardState = "${getBoardStateAsString()}\t${getDateString()}\t${linkedActivity.opponentId}\t$selectedCol:"
        if(gameIsOver) boardState += if(outOfSlots) "\t0" else "\t" + linkedActivity.playerId
        return boardState
    }

    fun setupToResumeGame(boardState: String) {
        val boardStateContents = boardState.split("\t")
        val nBoardStateIds = connect4BoardRows * connect4BoardCols
        val idOfPlayerWithNextTurn = boardStateContents[nBoardStateIds+1].toInt()
        isPlayersMove = linkedActivity.playerId != idOfPlayerWithNextTurn
        var lastMove = boardStateContents.last()
        if(!lastMove.contains(":")) lastMove = boardStateContents[boardStateContents.lastIndex-1]
        lastMove = lastMove.split(":")[0]
        val colOfLastMove = lastMove.toInt()
        setupPiecesToBoard(boardStateContents, colOfLastMove)
        determineNextEmptyRows()
        gameIsReady = true
        if(!isPlayersMove) addPiece(!isPlayer1, clickableSlots[colOfLastMove], true)
        else {
            val rowOfLastMove = nextEmptyRows[colOfLastMove]+1
            if(pieceConnectedFour(rowOfLastMove, colOfLastMove, isPlayer1)) {
                gameIsOver = true
                displayGameOverMessage(true)
            } else {
                countRemainingSlots()
                if(outOfSlots) {
                    gameIsOver = true
                    displayGameOverMessage(true)
                } else {
                    isPlayersMove = false
                    linkedActivity.waitForOpponentsMove()
                    updateMessageBarText(linkedActivity.getString(R.string.itsTheirTurnText))
                }
            }
        }
    }

    private fun determineNextEmptyRows() {
        for(col in 0 until connect4BoardCols)
            for(row in 0 until connect4BoardRows)
                if(!slots[row][col].isEmpty) {
                    nextEmptyRows[col] = row - 1
                    if(nextEmptyRows[col] < 0)
                        clickableSlots[col].moveTo(width.toFloat()*2, height.toFloat()*2)
                    break
                }
    }

    private fun setupPiecesToBoard(boardStateContents: List<String>, colOfLastMove: Int) {
        setupSlotsArray(boardStateContents)
        findLastMove(colOfLastMove)
        displayPieces()
        bringClickableBoardToFront()
    }

    private fun setupSlotsArray(boardStateContents: List<String>) {
        var index = 0
        for(row in 0 until connect4BoardRows)
            for(col in 0 until connect4BoardCols) {
                val id = boardStateContents[index++]
                val slot = slots[row][col]
                if(id != "O") {
                    slot.isEmpty = false
                    if(id == "R")
                        slot.isRedPiece = true
                }
            }
    }

    private fun findLastMove(colOfLastMove: Int) {
        for(row in 0 until connect4BoardRows)
            if(!slots[row][colOfLastMove].isEmpty) {
                if(!isPlayersMove) slots[row][colOfLastMove].isEmpty = true
                isPlayer1 = isPlayersMove == slots[row][colOfLastMove].isRedPiece
                break
            }
    }

    private fun displayPieces() {
        for(row in 0 until connect4BoardRows) {
            for(col in 0 until connect4BoardCols) {
                val slot = slots[row][col]
                if(!slot.isEmpty) {
                    val piece = createPiece(slot.isRedPiece)
                    val pieces = getPieces(slot.isRedPiece)
                    pieces.push(piece)
                    add(pieces.last(), slot.slot.x, slot.slot.y)
                }
            }
        }
    }

    private fun bringClickableBoardToFront() {
        board.sendToFront()
        for(row in 0 until connect4BoardRows)
            for(col in 0 until connect4BoardCols)
                slots[row][col].slot.sendToFront()
    }

    private fun getBoardStateAsString(): String {
        var boardState = ""
        for(row in 0 until connect4BoardRows) {
            for(col in 0 until connect4BoardCols) {
                val slot = slots[row][col]
                boardState += when {
                    slot.isEmpty -> "O"
                    slot.isRedPiece -> "R"
                    else -> "Y"
                }
                if(row != connect4BoardRows-1 || col != connect4BoardCols-1)
                    boardState += "\t"
            }
        }
        return boardState
    }

    private fun runOnlineOptionFunctions(motionEvent: MotionEvent) {
        if(gameIsReady && !actionInProgress && !animationInProgress) {
            checkIfButtonsClickedForOnlineOption(motionEvent)
            if(isPlayersMove) {
                checkIfPieceClicked(motionEvent, true)
                checkIfSlotClicked(motionEvent, true)
            }
        }
    }

    private fun checkIfButtonsClickedForOnlineOption(motionEvent: MotionEvent) {
        when (getElementAt(GPoint(motionEvent.x, motionEvent.y))) {
            menuButton -> {
                preventButtonClickUntilDialogAppears()
                linkedActivity.openMenu()
            }
            messagesButton -> {
                preventButtonClickUntilDialogAppears()
                linkedActivity.openMessagesForOnlineOption(messages)
            }
            restartButton -> {
                preventButtonClickUntilDialogAppears()
                linkedActivity.restartOnlineGameButtonClicked()
            }
            prevMessageButton -> displayPreviousMessage()
            nextMessageButton -> displayNextMessage()
        }
    }

    private fun checkIfSlotClicked(motionEvent: MotionEvent, forOnline: Boolean = false) {
        val isRedPiece = if (forOnline) isPlayer1 else isPlayersMove
        if(gameIsOver) return
        for((index, slot) in clickableSlots.withIndex())
            if (getElementAt(GPoint(motionEvent.x, motionEvent.y)) == slot) {
                selectedCol = index
                if(slotSelected) movePiece(isRedPiece, slot)
                else addPiece(isRedPiece, slot)
                break
            }
        for(row in 0 until connect4BoardRows)
            for(col in 0 until connect4BoardCols)
                if (getElementAt(GPoint(motionEvent.x, motionEvent.y)) == slots[row][col].slot) {
                    if (slotSelected) {
                        if (col == selectedCol) dropPiece(forOnline)
                        else movePiece(isRedPiece, clickableSlots[col])
                    } else addPiece(isRedPiece, clickableSlots[col])
                }
    }

    private fun createPiece(isRedPiece: Boolean): GOval {
        val piece = GOval(slotSize.toFloat(), slotSize.toFloat())
        val paint = Paint()
        if(isRedPiece) paint.setARGB(255, redR, redG, redB)
        else paint.setARGB(255, yellowR, yellowG, yellowB)
        piece.paint = paint
        return piece
    }

    private fun movePiece(isRedPiece: Boolean, slot: GOval) {
        val index = clickableSlots.indexOf(slot)
        if(nextEmptyRows[index] < 0) return
        selectedCol = index
        val piece = getPieces(isRedPiece).last()
        val tempPiece = createPiece(isRedPiece)
        add(tempPiece, piece.x, piece.y)

        val r = if(isRedPiece) redR else yellowR
        val g = if(isRedPiece) redG else yellowG
        val b = if(isRedPiece) redB else yellowB

        val paint = Paint()
        paint.setARGB(0, r, g, b)
        piece.paint = paint
        piece.moveTo(slot.x, slot.y)

        val nChangeAlpha = (255f / connect4AnimateFrames).toInt()
        var pieceCurrentAlpha = 0
        var tempCurrentAlpha = 255

        var count = 1
        animationInProgress = true
        animate(connect4FPS) {
            if(count <= connect4AnimateFrames) {
                pieceCurrentAlpha += nChangeAlpha
                tempCurrentAlpha -= nChangeAlpha
                if(pieceCurrentAlpha < 255)
                    piece.paint.setARGB(pieceCurrentAlpha, r, g, b)
                if(tempCurrentAlpha > 0)
                    tempPiece.paint.setARGB(tempCurrentAlpha, r, g, b)

                if(count++ == connect4AnimateFrames) {
                    piece.paint.setARGB(255, r, g, b)
                    remove(tempPiece)

                    slotSelected = true
                    animationInProgress = false
                    animationStop()
                }
            }
        }
    }

    private fun addPiece(isRedPiece: Boolean, slot: GOval, isOpponentsMove: Boolean = false) {
        val index = clickableSlots.indexOf(slot)
        if(nextEmptyRows[index] < 0) return
        selectedCol = index
        val pieces = getPieces(isRedPiece)
        pieces.push(createPiece(isRedPiece))
        val piece = pieces[pieces.lastIndex]

        val r = if(isRedPiece) redR else yellowR
        val g = if(isRedPiece) redG else yellowG
        val b = if(isRedPiece) redB else yellowB

        val paint = Paint()
        paint.setARGB(0, r, g, b)
        piece.paint = paint
        add(piece, slot.x, slot.y)

        val nChangeAlpha = (255f / connect4AnimateFrames).toInt()
        var pieceCurrentAlpha = 0

        var count = 1
        animationInProgress = true
        animate(connect4FPS) {
            if(count <= connect4AnimateFrames) {
                pieceCurrentAlpha += nChangeAlpha
                if(pieceCurrentAlpha < 255)
                    piece.paint.setARGB(pieceCurrentAlpha, r, g, b)

                if(count++ == connect4AnimateFrames) {
                    piece.paint.setARGB(255, r, g, b)

                    slotSelected = true
                    animationInProgress = false
                    animationStop()
                    if(isOpponentsMove)
                        dropPiece(forOnline = true, isOpponentsMove = true)
                }
            }
        }
    }

    private fun getPieces(forPlayer1: Boolean): Stack<GOval> {
        return if(forPlayer1) redPieces else yellowPieces
    }

    private fun displayPreviousMessage() {
        if (displayedMessageIndex > 0) {
            updateMessageBarText(
                messages[--displayedMessageIndex],
                false
            )
            if (displayedMessageIndex < messages.lastIndex &&
                nextMessageButton.bitmap != nextMessageButtonImages[0])
                nextMessageButton.bitmap = nextMessageButtonImages[0]
            if (displayedMessageIndex == 0)
                prevMessageButton.bitmap = prevMessageButtonImages[1]
        }
    }

    private fun displayNextMessage() {
        if (displayedMessageIndex < messages.lastIndex) {
            if (displayedMessageIndex == 0)
                prevMessageButton.bitmap = prevMessageButtonImages[0]
            updateMessageBarText(
                messages[++displayedMessageIndex],
                false
            )
            if (displayedMessageIndex == messages.lastIndex)
                nextMessageButton.bitmap = nextMessageButtonImages[1]
        }
    }

    private fun preventButtonClickUntilDialogAppears() {
        actionInProgress = true
        val timer = object : CountDownTimer(dialogDelay, dialogDelay) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                actionInProgress = false
            }
        }
        timer.start()
    }

    // required override functions to implement GestureDetector
    override fun onDown(e: MotionEvent?): Boolean { return false }
    override fun onShowPress(e: MotionEvent?) {}
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?,
                          distanceX: Float, distanceY: Float): Boolean { return false }
    override fun onLongPress(e: MotionEvent?) {}
    override fun onFling(e1: MotionEvent?, e2: MotionEvent?,
                         velocityX: Float, velocityY: Float): Boolean { return false }

    // Status Booleans
    private var animationInProgress = false
    private var actionInProgress = false
    private var gameIsReady = false
    private var gameIsOver = false
    private var outOfSlots = false
    private var slotSelected = false

    // Element Sizing (lateinit)
    private var boardWidth = .0
    private var boardHeight = .0
    private var elementSpacing = .0
    private var slotSize = .0
    private var slotSpacing = .0
    private var offsetSide = .0
    private var messageAreaWidth = .0
    private var messagesBarWidth = .0
    private var buttonWidth = .0
    private var buttonHeight = .0
    private var fontSize = .0

    // Selected Slot
    private var selectedRow = -1
    private var selectedCol = -1

    // Font Size in 1080 Width
    private val fontSizeIn1080 = 50.0
    private val elementSpacingIn1080 = 20.0

    // Images
    private val prevMessageButtonImages = ArrayList<Bitmap>()
    private val nextMessageButtonImages = ArrayList<Bitmap>()

    // Connect 4 Elements
    private val clickableSlots = ArrayList<GOval>()
    private val redPieces = Stack<GOval>()
    private val yellowPieces = Stack<GOval>()
    private val nextEmptyRows = ArrayList<Int>()
    private val connectedPieces = Stack<Slot>()
    private lateinit var board: GSprite
    private lateinit var slots: Array<Array<Slot>>

    // Top Elements
    private var messagesBar = GCompound()
    private var menuButton = GCompound()
    private var restartButton = GCompound()
    private lateinit var messagesBarLabel: GLabel
    private lateinit var messagesButton: GSprite
    private lateinit var prevMessageButton: GSprite
    private lateinit var nextMessageButton: GSprite

    // Screen Distribution Percentages
    private val percentOfWidthTheBoardTakes = .9
    private val slotSizeIncreasePercent = 1.1
    private val slotShiftingIndex = 1

    private fun buildGame() {
        setSizes()
        setMessagesBar()
        initBoard()
        initSlots()
        initAboveSlots()
        setOnscreenElements()
    }

    private fun initBoard() {
        var boardImage = BitmapFactory.decodeResource(resources, R.drawable.connect4board)
        boardImage = Bitmap.createScaledBitmap(boardImage, boardWidth.toInt(),
            boardHeight.toInt(), false)
        board = GSprite(boardImage)
        add(board, offsetSide.toFloat(), (height - boardHeight - offsetSide - buttonHeight - elementSpacing).toFloat())
    }

    private fun initSlots() {
        slots = Array(6) { Array(7) { Slot() } }
        for(row in 0 until 6) for(col in 0 until 7) {
            val slot = slots[row][col]
            val paint = Paint()
            paint.setARGB(0, 0, 0, 0)
            slot.isEmpty = true
            slot.isRedPiece = false
            slot.slot = GOval(slotSize.toFloat(), slotSize.toFloat())
            slot.slot.paint = paint
            slot.slot.x = (board.x + slotSpacing + col * (slotSize + slotSpacing)).toFloat()
            slot.slot.y = (board.y + slotSpacing + row * (slotSize + slotSpacing)).toFloat()
            add(slot.slot)
        }
    }

    private fun initAboveSlots() {
        val startX = offsetSide + slotSpacing
        for(i in 0 until 7) {
            nextEmptyRows.add(connect4BoardRows-1)
            val slot = GOval(slotSize.toFloat(), slotSize.toFloat())
            val paint = Paint()
            paint.color = ContextCompat.getColor(context, R.color.lightGray)
            slot.paint = paint
            clickableSlots.add(slot)
            add(clickableSlots[i],
                (startX + i * (slotSize + slotSpacing) - slotShiftingIndex).toFloat(),
                (board.y - slotSpacing - slotSize).toFloat())
        }
    }

    private fun setOnscreenElements() {
        setMenuButton()
        setRestartButton()
        setMessagesButton()
    }

    private fun setSizes() {
        fontSize = width / (1080.0 / fontSizeIn1080)
        elementSpacing = width / (1080.0 / elementSpacingIn1080)
        boardWidth = percentOfWidthTheBoardTakes * width
        slotSize = (.1 * boardWidth) * slotSizeIncreasePercent
        slotSpacing = (boardWidth - (slotSize * 7) ) / 8
        boardHeight = slotSize * 6 + slotSpacing * 7
        offsetSide = (width - boardWidth) / 2
    }

    private fun setMessagesBar() {
        val messagesBarSprite = GSprite(resizeMessagesBar(
            BitmapFactory.decodeResource(resources, R.drawable.messages_bar)))
        messagesBarWidth = messagesBarSprite.width.toDouble()
        messagesBar.add(messagesBarSprite)
        messagesBarLabel = GLabel("")
        messagesBarLabel.paint =
            getButtonTextPaint(ContextCompat.getColor(context, R.color.darkGray))
        messagesBarLabel.fontSize = fontSize.toFloat()
        messagesBar.add(messagesBarLabel,
            (messagesBarSprite.width - messagesBarLabel.width) / 2,
            (buttonHeight - messagesBarLabel.height).toFloat() / 2)
        add(messagesBar, offsetSide.toFloat(), (height - buttonHeight - offsetSide).toFloat())
        setMessagesBarArrows()
    }

    // messagesBarWidth = (A * (D - C)) / (A + B)
    // A = image width; B = image height; C = element spacing; D = board width
    private fun resizeMessagesBar(image: Bitmap): Bitmap {
        val messagesBarWidth = (image.width * (boardWidth - elementSpacing)) /
                (image.width + image.height)
        buttonHeight = image.height * (messagesBarWidth / image.width)
        return Bitmap.createScaledBitmap(image, messagesBarWidth.toInt(),
            buttonHeight.toInt(), false)
    }

    private fun getButtonTextPaint(color: Int): Paint {
        val buttonTextPaint = Paint()
        buttonTextPaint.color = color
        buttonTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        return buttonTextPaint
    }

    private fun setMessagesBarArrows() {
        setPrevMessageButton()
        setNextMessageButton()
        val messageLeftBounds = prevMessageButton.x + prevMessageButton.width + elementSpacing
        val messageRightBounds = nextMessageButton.x - elementSpacing
        messageAreaWidth = messageRightBounds - messageLeftBounds
    }

    private fun setPrevMessageButton() {
        prevMessageButtonImages.add(resizeArrowImage(BitmapFactory.decodeResource(resources,
            R.drawable.right_arrow_icon).rotate(180f)))
        prevMessageButtonImages.add(resizeArrowImage(BitmapFactory.decodeResource(resources,
            R.drawable.right_arrow_icon_light).rotate(180f)))
        prevMessageButton = GSprite(prevMessageButtonImages[1])
        add(prevMessageButton, messagesBar.x,
            messagesBar.y + (buttonHeight - prevMessageButton.height).toFloat() / 2)
    }

    private fun setNextMessageButton() {
        nextMessageButtonImages.add(resizeArrowImage(BitmapFactory.decodeResource(resources,
            R.drawable.right_arrow_icon)))
        nextMessageButtonImages.add(resizeArrowImage(BitmapFactory.decodeResource(resources,
            R.drawable.right_arrow_icon_light)))
        nextMessageButton = GSprite(nextMessageButtonImages[1])
        add(nextMessageButton,
            messagesBar.x + messagesBarWidth.toFloat() - nextMessageButton.width,
            messagesBar.y + (buttonHeight - nextMessageButton.height).toFloat() / 2)
    }

    private fun resizeArrowImage(image: Bitmap): Bitmap {
        val imageHeight = buttonHeight
        val imageWidth = image.width * (imageHeight / image.height)
        return Bitmap.createScaledBitmap(image, imageWidth.toInt(), imageHeight.toInt(), false)
    }

    private fun setMenuButton() {
        val buttonSprite = GSprite(resizeButtonImage(
            BitmapFactory.decodeResource(resources, R.drawable.button_image)))
        menuButton.add(buttonSprite)
        val menuButtonLabel = GLabel(resources.getString(R.string.menuText))
        menuButtonLabel.paint = getButtonTextPaint(Color.WHITE)
        menuButtonLabel.fontSize = fontSize.toFloat()
        menuButton.add(menuButtonLabel, (buttonWidth - menuButtonLabel.width).toFloat() / 2,
            (buttonHeight - menuButtonLabel.height).toFloat() / 2)
        add(menuButton, (width - buttonWidth).toFloat() / 2,
            (width - boardWidth).toFloat() / 2)
    }

    private fun resizeButtonImage(image: Bitmap): Bitmap {
        buttonWidth = image.width * (buttonHeight / image.height)
        return Bitmap.createScaledBitmap(image, buttonWidth.toInt(), buttonHeight.toInt(), false)
    }

    private fun setRestartButton() {
        val buttonSprite = GSprite(resizeButtonImage(
            BitmapFactory.decodeResource(resources, R.drawable.button_image)))
        restartButton.add(buttonSprite)
        val restartButtonLabel = GLabel(resources.getString(R.string.restartText))
        restartButtonLabel.paint = getButtonTextPaint(Color.WHITE)
        restartButtonLabel.fontSize = fontSize.toFloat()
        restartButton.add(restartButtonLabel, (buttonWidth - restartButtonLabel.width).toFloat() / 2,
            (buttonHeight - restartButtonLabel.height).toFloat() / 2)
        add(restartButton, (width - buttonWidth).toFloat() / 2,
            (menuButton.y + buttonHeight + elementSpacing).toFloat())
    }

    private fun resizeIconImage(iconImage: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(iconImage,
            buttonHeight.toInt(), buttonHeight.toInt(), false)
    }

    private fun setMessagesButton() {
        val messagesButtonImage = BitmapFactory.decodeResource(resources, R.drawable.messages_icon)
        messagesButton = GSprite(resizeIconImage(messagesButtonImage))
        add(messagesButton, (messagesBar.x + boardWidth - messagesButton.width).toFloat(), messagesBar.y)
    }

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
}