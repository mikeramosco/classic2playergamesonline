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

class MastermindCanvas(context: Context, attrs: AttributeSet)
    : GCanvas(context, attrs), View.OnTouchListener, GestureDetector.OnGestureListener {

    // Gesture Detector
    private lateinit var mGestureDetector: GestureDetector

    // Battleship Activity
    lateinit var linkedActivity: MastermindActivity

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

    // True if this player setup the pattern - Online option only
    private var playerSetupPattern = false

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
        updateMessageBarText(linkedActivity.getString(R.string.setupPatternPrompt))
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
        gameIsReady = true
        if (playerIsHost == hostMovesFirst) {
            playerSetupPattern = true
            updateMessageBarText(linkedActivity.getString(R.string.setupPatternPrompt))
        } else {
            playerSetupPattern = false
            updateMessageBarText(linkedActivity.getString(R.string.waitForPatternSetupPrompt))
            linkedActivity.waitForOpponentsMove(true)
            moveBoards()
        }
    }

    fun getOpponentsMove(boardState: String): Boolean {
        if(boardState.contains("!!")) return false
        val boardStateContents = boardState.split("\t")
        var lastMove = boardStateContents.last()
        if(!lastMove.contains(":"))
            lastMove = boardStateContents[boardStateContents.lastIndex-1]
        val lastMoveContents = lastMove.split(":")
        return if(playerSetupPattern) {
            if(lastMoveContents.size == mastermindBoardCols) return false
            for(i in 0 until mastermindBoardCols)
                patternEntry[i] = lastMoveContents[i].toInt()
            rightPlaceMatchCount = lastMoveContents[mastermindBoardCols].toInt()
            wrongPlaceMatchCount = lastMoveContents[mastermindBoardCols+1].toInt()
            currentGuessDisplayIndex = lastMoveContents[mastermindBoardCols+2].toInt()
            displayGuess()
            checkIfGameOver()
            false
        } else {
            patternIsSet = true
            for(i in 0 until mastermindBoardCols)
                setPattern[i] = lastMoveContents[i].toInt()
            updateMessageBarText(linkedActivity.getString(R.string.guessThePatternPrompt))
            true
        }
    }

    fun setupToResumeGame(boardState: String) {
        playerSetupPattern = playerIsHost == hostMovesFirst
        val boardStateContents = boardState.split("\t")
        val nBoardStateIds = (mastermindLeftBoardRows + mastermindRightBoardRows) *
                mastermindBoardCols * 2 + mastermindBoardCols
        var lastMove = boardStateContents.last()
        if(!lastMove.contains(":"))
            lastMove = boardStateContents[boardStateContents.lastIndex-1]
        val lastMoveContents = lastMove.split(":")
        var setPatternIndex = nBoardStateIds - mastermindBoardCols
        for(i in 0 until mastermindBoardCols)
            setPattern[i] = boardStateContents[setPatternIndex++].toInt()
        patternIsSet = true
        gameIsReady = true
        if(playerSetupPattern)
            displaySetPattern()
        moveBoards()
        if(lastMoveContents.size > mastermindBoardCols) {
            rightPlaceMatchCount = lastMoveContents[mastermindBoardCols].toInt()
            wrongPlaceMatchCount = lastMoveContents[mastermindBoardCols+1].toInt()
            currentGuessDisplayIndex = lastMoveContents[mastermindBoardCols+2].toInt() +
                    mastermindBoardCols
            setupBoardToResume(boardStateContents)
        }
        checkIfGameOver()
        if(!gameIsOver) {
            val message =
                if (playerSetupPattern) linkedActivity.getString(R.string.opponentWillGuessPrompt)
                else linkedActivity.getString(R.string.guessThePatternPrompt)
            updateMessageBarText(message)
            if(playerSetupPattern)
                linkedActivity.waitForOpponentsMove()
        }
    }

    private fun setupBoardToResume(boardStateContents: List<String>) {
        val totalRows = mastermindRightBoardRows + mastermindLeftBoardRows
        var boardStateIndex = 0
        for(row in 0 until totalRows) {
            for(col in 0 until mastermindBoardCols) {
                val index = row * mastermindBoardCols + col
                val paint = Paint()
                paint.color = getOptionColor(boardStateContents[boardStateIndex++].toInt())
                displayedPegs[index].paint = paint
            }
            for(col in 0 until mastermindBoardCols) {
                val index = row * mastermindBoardCols + col
                val paint = Paint()
                paint.color = getOptionColor(boardStateContents[boardStateIndex++].toInt())
                displayedScorePegs[index].paint = paint
            }
            if(boardStateIndex == currentGuessDisplayIndex * 2) break
        }
    }

    private fun displaySetPattern() {
        for(i in 0 until mastermindBoardCols) {
            val paint = Paint()
            paint.color = getOptionColor(setPattern[i])
            entryPegs[i].paint = paint
        }
    }

    private fun resetGame() {
        patternIsSet = false
        gameIsOver = false
        pegSelected = -1
        optionSelected = -1
        currentGuessDisplayIndex = 0
        guessingBoardsAreHidden = true
        val paint = Paint()
        paint.color = ContextCompat.getColor(context, R.color.mediumGray)
        for(i in 0 until mastermindBoardCols) {
            patternEntry[i] = -1
            setPattern[i] = -1
            entryPegs[i].paint = paint
        }
        for((index, peg) in displayedPegs.withIndex()) {
            peg.paint = paint
            displayedScorePegs[index].paint = paint
        }
    }

    fun initRestartGame(forOnline: Boolean) {
        if(guessingBoardsAreHidden)
            if(forOnline) restartOnlineGame()
            else restartSameScreenGame()
        else moveBoards(hideBoards = true, forOnline = forOnline)
    }

    private fun restartSameScreenGame() {
        resetGame()
        updateMessageBarText(linkedActivity.getString(R.string.setupPatternPrompt))
    }

    private fun restartOnlineGame() {
        resetGame()
        updateMessageBarText("")
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
            checkIfPegClicked(motionEvent)
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
            submitEntryButton -> submitPattern()
        }
    }

    private fun submitPattern(forOnline: Boolean = false) {
        if(gameIsOver || forOnline && playerSetupPattern == patternIsSet) return
        if(allPegsFilled()) {
            if (patternIsSet) {
                countMatches()
                displayGuess()
                checkIfGameOver()
            } else {
                patternIsSet = true
                setPattern()
                moveBoards()
            }
            if (forOnline)
                linkedActivity.updateBoardStateInFirebase(getBoardStateToUpdateFirebase())
            if (forOnline && !playerSetupPattern && !gameIsOver || !forOnline && !gameIsOver ||
                forOnline && !playerSetupPattern && rightPlaceMatchCount == 4 ||
                !forOnline && rightPlaceMatchCount == 4) resetEntryPegs()
            if(forOnline && playerSetupPattern) updateMessageBarText(linkedActivity.getString(R.string.opponentWillGuessPrompt))
        }
    }

    private fun getLastMove(): String {
        var lastMove = ""
        if(playerSetupPattern) {
            for ((index, entry) in patternEntry.withIndex()) {
                lastMove += "$entry"
                if (index != patternEntry.lastIndex)
                    lastMove += ":"
            }
            linkedActivity.waitForOpponentsMove()
        } else {
            for (entry in patternEntry)
                lastMove += "$entry:"
            lastMove += "$rightPlaceMatchCount:$wrongPlaceMatchCount:" +
                    "${currentGuessDisplayIndex-mastermindBoardCols}"
        }
        return lastMove
    }

    private fun getBoardStateToUpdateFirebase(): String {
        val idOfNextPlayersTurn = if(playerSetupPattern) linkedActivity.opponentId else linkedActivity.playerId
        var boardState = "${getBoardStateAsString()}\t${getDateString()}\t$idOfNextPlayersTurn\t${getLastMove()}"
        if(gameIsOver) boardState +=
            if(rightPlaceMatchCount == 4) "\t" + linkedActivity.playerId
            else "\t" + linkedActivity.opponentId
        return boardState
    }

    private fun getBoardStateAsString(): String {
        var boardState = ""
        val totalRows = mastermindRightBoardRows + mastermindLeftBoardRows
        for(row in 0 until totalRows) {
            for(col in 0 until mastermindBoardCols) {
                val index = row * mastermindBoardCols + col
                boardState += "${getColorIndex(displayedPegs[index].paint.color)}\t"
            }
            for(col in 0 until mastermindBoardCols) {
                val index = row * mastermindBoardCols + col
                boardState += "${getColorIndex(displayedScorePegs[index].paint.color)}\t"
            }
        }
        for(i in 0 until mastermindBoardCols) {
            boardState += setPattern[i].toString()
            if(i != mastermindBoardCols - 1)
                boardState += "\t"
        }
        return boardState
    }

    private fun getColorIndex(color: Int): Int {
        return when(color) {
            Color.BLACK -> 0
            Color.WHITE -> 1
            Color.RED -> 2
            Color.BLUE -> 3
            ContextCompat.getColor(context, R.color.green) -> 4
            ContextCompat.getColor(context, R.color.orange) -> 5
            else -> -1
        }
    }

    private fun countMatches() {
        val guessPatternMatches = ArrayList<Boolean>()
        val answerPatternMatches = ArrayList<Boolean>()
        rightPlaceMatchCount = 0
        wrongPlaceMatchCount = 0

        for(i in 0 until mastermindBoardCols)
            if(patternEntry[i] == setPattern[i]) {
                guessPatternMatches.add(true)
                answerPatternMatches.add(true)
                rightPlaceMatchCount++
            } else {
                guessPatternMatches.add(false)
                answerPatternMatches.add(false)
            }

        for((guessIndex, guessMatched) in guessPatternMatches.withIndex())
            if(!guessMatched) for((answerIndex, answerMatched) in answerPatternMatches.withIndex())
                if(!answerMatched && patternEntry[guessIndex] == setPattern[answerIndex]) {
                    guessPatternMatches[guessIndex] = true
                    answerPatternMatches[answerIndex] = true
                    wrongPlaceMatchCount++
                    break
                }
    }

    private fun displayGuess() {
        var scoreDisplayIndex = currentGuessDisplayIndex
        for(i in 0 until mastermindBoardCols) {
            val paint = Paint()
            paint.color = getOptionColor(patternEntry[i])
            displayedPegs[currentGuessDisplayIndex++].paint = paint
        }
        for(i in 0 until rightPlaceMatchCount) {
            val paint = Paint()
            paint.color = Color.BLACK
            displayedScorePegs[scoreDisplayIndex++].paint = paint
        }
        for(i in 0 until wrongPlaceMatchCount) {
            val paint = Paint()
            paint.color = Color.WHITE
            displayedScorePegs[scoreDisplayIndex++].paint = paint
        }
    }

    private fun checkIfGameOver() {
        val totalPegs = (mastermindRightBoardRows + mastermindLeftBoardRows) * mastermindBoardCols
        var message = ""
        if(rightPlaceMatchCount == 4) {
            message = if(sameScreenOptionSelected ||
                !sameScreenOptionSelected && !playerSetupPattern)
                linkedActivity.getString(R.string.gameOverGuessedPatternPrompt)
            else linkedActivity.getString(R.string.gameOverTheyGuessedPatternPrompt)
            gameIsOver = true
        } else if(currentGuessDisplayIndex == totalPegs) {
            message = if(sameScreenOptionSelected ||
                !sameScreenOptionSelected && !playerSetupPattern)
                linkedActivity.getString(R.string.gameOverOutOfGuessesPrompt)
            else linkedActivity.getString(R.string.gameOverTheyreOutOfGuessesPrompt)
            revealAnswer()
            gameIsOver = true
        }
        if(gameIsOver) updateMessageBarText(message)
    }

    private fun revealAnswer() {
        for(i in 0 until mastermindBoardCols) {
            val paint = Paint()
            paint.color = getOptionColor(setPattern[i])
            entryPegs[i].paint = paint
        }
    }

    private fun allPegsFilled(): Boolean {
        for(entry in patternEntry) if(entry == -1) return false; return true
    }

    private fun setPattern() {
        for(i in 0 until setPattern.size) setPattern[i] = patternEntry[i]
    }

    private fun unselectOptions() {
        pegSelected = -1
        optionSelected = -1
        pegSelection.moveTo(width.toFloat() * 2, height.toFloat() * 2)
        optionSelection.moveTo(width.toFloat() * 2, height.toFloat() * 2)
    }

    private fun moveBoards(hideBoards: Boolean = false, forOnline: Boolean = false) {
        unselectOptions()
        if(sameScreenOptionSelected) updateMessageBarText(linkedActivity.getString(R.string.guessThePatternPrompt))
        var guessingBoardsVelocity = widthToMoveBoard.toFloat() / mastermindAnimateFrames.toFloat()
        if(hideBoards) guessingBoardsVelocity = -guessingBoardsVelocity
        var entryBoardVelocity = widthToMoveEntryBoard.toFloat() / mastermindAnimateFrames.toFloat()
        if(hideBoards) entryBoardVelocity = -entryBoardVelocity
        val nRightBoardPegs = mastermindLeftBoardRows * mastermindBoardCols
        val totalPegs = (mastermindRightBoardRows + mastermindLeftBoardRows) * mastermindBoardCols
        var count = 1
        animationInProgress = true
        animate(mastermindFPS) {
            if(count <= mastermindAnimateFrames) {
                boards[0].moveBy(guessingBoardsVelocity, 0f)
                for(i in 0 until nRightBoardPegs) {
                    displayedPegs[i].moveBy(guessingBoardsVelocity, 0f)
                    displayedScorePegs[i].moveBy(guessingBoardsVelocity, 0f)
                }

                boards[1].moveBy(-guessingBoardsVelocity, 0f)
                for(i in nRightBoardPegs until totalPegs) {
                    displayedPegs[i].moveBy(-guessingBoardsVelocity, 0f)
                    displayedScorePegs[i].moveBy(-guessingBoardsVelocity, 0f)
                }

                boards[2].moveBy(entryBoardVelocity, 0f)
                submitEntryButton.moveBy(entryBoardVelocity, 0f)
                for(peg in entryPegs) peg.moveBy(entryBoardVelocity, 0f)
                for(option in optionPegs) option.moveBy(entryBoardVelocity, 0f)

                if (count++ == mastermindAnimateFrames) {
                    animationInProgress = false
                    animationStop()
                    if(hideBoards)
                        if(forOnline) restartOnlineGame()
                        else restartSameScreenGame()
                    else guessingBoardsAreHidden = false
                }
            }
        }
    }

    private fun resetEntryPegs() {
        unselectOptions()
        val paint = Paint()
        paint.color = ContextCompat.getColor(context, R.color.mediumGray)
        for((index, peg) in entryPegs.withIndex()) {
            peg.paint = paint
            patternEntry[index] = -1
        }
    }

    private fun runOnlineOptionFunctions(motionEvent: MotionEvent) {
        if(gameIsReady && !actionInProgress && !animationInProgress) {
            checkIfButtonsClickedForOnlineOption(motionEvent)
            if(!playerSetupPattern && patternIsSet || playerSetupPattern && !patternIsSet)
                checkIfPegClicked(motionEvent)
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
            submitEntryButton -> submitPattern(true)
        }
    }

    private fun checkIfPegClicked(motionEvent: MotionEvent) {
        if(gameIsOver) return
        for((index, peg) in entryPegs.withIndex())
            if (getElementAt(GPoint(motionEvent.x, motionEvent.y)) == peg) selectPeg(peg, index)
        for((index, option) in optionPegs.withIndex())
            if (getElementAt(GPoint(motionEvent.x, motionEvent.y)) == option) selectOption(option, index)
    }

//    private val unselectOptionTimer = object : CountDownTimer(unselectOptionDelay, unselectOptionDelay) {
//        override fun onTick(millisUntilFinished: Long) { }
//        override fun onFinish() {
//            optionSelection.moveTo(width.toFloat() * 2, height.toFloat() * 2)
//            optionSelected = -1
//        }
//    }
//
//    private var optionTimerIsOff = true

    private fun selectPeg(peg: GOval, index: Int) {
        if(optionSelected != -1) {
            val paint = Paint()
            paint.color = getOptionColor(optionSelected)
            entryPegs[index].paint = paint
            patternEntry[index] = optionSelected

            optionSelection.moveTo(width.toFloat() * 2, height.toFloat() * 2)
            optionSelected = -1

//            if(optionTimerIsOff) {
//                optionTimerIsOff = false // turn it on
//                unselectOptionTimer.start()
//            } else {
//                unselectOptionTimer.cancel() // cancel
//                unselectOptionTimer.start()  // then restart
//            }
        } else when(pegSelected) {
            index -> {
                pegSelection.moveTo(width.toFloat() * 2, height.toFloat() * 2)
                pegSelected = -1
            }
            -1 -> {
                pegSelection.moveTo(peg.x + (peg.width - pegSelection.width) / 2,
                    peg.y + (peg.height - pegSelection.height) / 2)
                pegSelected = index
            }
            else -> {
                val selectedPegPaint = Paint()
                val selectedPegColor = entryPegs[pegSelected].paint.color
                patternEntry[index] = getColorIndex(selectedPegColor)
                selectedPegPaint.color = selectedPegColor

                val clickedPegPaint = Paint()
                val clickedPegColor = entryPegs[index].paint.color
                patternEntry[pegSelected] = getColorIndex(clickedPegColor)
                clickedPegPaint.color = clickedPegColor

                entryPegs[index].paint = selectedPegPaint
                entryPegs[pegSelected].paint = clickedPegPaint

                pegSelection.moveTo(width.toFloat() * 2, height.toFloat() * 2)
                pegSelected = -1
            }
        }
    }

    private fun selectOption(option: GOval, index: Int) {
        if(pegSelected != -1) {
            val paint = Paint()
            paint.color = getOptionColor(index)
            entryPegs[pegSelected].paint = paint
            patternEntry[pegSelected] = index
            pegSelection.moveTo(width.toFloat() * 2, height.toFloat() * 2)
            pegSelected = -1
        } else when(optionSelected) {
            index -> {
                optionSelection.moveTo(width.toFloat() * 2, height.toFloat() * 2)
                optionSelected = -1
            } else -> {
                optionSelection.moveTo(option.x + (option.width - optionSelection.width) / 2,
                    option.y + (option.height - optionSelection.height) / 2)
                optionSelected = index
//                if(!optionTimerIsOff)
//                    unselectOptionTimer.cancel()
            }
        }
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
    private var patternIsSet = false
    private var animationInProgress = false
    private var actionInProgress = false
    private var gameIsReady = false
    private var gameIsOver = false
    private var guessingBoardsAreHidden = true

    // Pattern Guess Tracking
    private var patternEntry = ArrayList<Int>()
    private var setPattern = ArrayList<Int>()
    private var pegSelected = -1
    private var optionSelected = -1
    private var rightPlaceMatchCount = 0
    private var wrongPlaceMatchCount = 0
    private var currentGuessDisplayIndex = 0

    // Mastermind Elements
    private lateinit var submitEntryButton: GSprite
    private lateinit var pegSelection: GOval
    private lateinit var optionSelection: GOval
    private val entryPegs = ArrayList<GOval>()
    private val optionPegs = ArrayList<GOval>()
    private val displayedPegs = ArrayList<GOval>()
    private val displayedScorePegs = ArrayList<GOval>()
    private val boards = ArrayList<GRect>()

    // Element Sizing (lateinit)
    private var elementSpacing = .0
    private var boardWidth = .0
    private var offsetSide = .0
    private var pegOffsetTopOfBoard = .0
    private var boardsAreaHeight = .0
    private var boardsAreaWidth = .0
    private var messageAreaWidth = .0
    private var messagesBarWidth = .0
    private var buttonWidth = .0
    private var buttonHeight = .0
    private var fontSize = .0
    private var optionPegSize = .0
    private var optionPegSpacing = .0
    private var selectedOptionPegSize = .0
    private var selectedPegSize = .0
    private var pegSize = .0
    private var pegSpacing = .0
    private var scorePegSize = .0
    private var scorePegSpacing = .0
    private var widthToMoveBoard = .0
    private var widthToMoveEntryBoard = .0
    private var displayedPegsVerticalSpacing = .0

    // Font Size in 1080 Width
    private val fontSizeIn1080 = 50.0
    private val elementSpacingIn1080 = 20.0

    // Images
    private val prevMessageButtonImages = ArrayList<Bitmap>()
    private val nextMessageButtonImages = ArrayList<Bitmap>()

    // Top Elements
    private var messagesBar = GCompound()
    private var menuButton = GCompound()
    private var restartButton = GCompound()
    private lateinit var messagesBarLabel: GLabel
    private lateinit var messagesButton: GSprite
    private lateinit var prevMessageButton: GSprite
    private lateinit var nextMessageButton: GSprite

    // Screen Distribution Percentages
    private val submitGuessButtonHeightPercentToOptionsArea = .75
    private val percentOfRemainingHeightTheBoardTakes = .80
    private val selectedPegPercentToRelativePeg = 1.42
    private val pegSpacingPercentToRelativePeg = .33
    private val percentOfHeightTopButtonsTake = .175
    private val percentOfWidthTheBoardTakes = .9
    private val pegPercentOffsetTopOfBoard = .75
    private val pegPercentToBoardWidth = .16
    private val optionPegPercentToPeg = .77
    private val scorePegPercentToPeg = .5

    private fun buildGame() {
        setSizes()
        initValues()
        setOnscreenElements()
        buildBoards()
        setupForPatternSetup()
    }

    private fun setupForPatternSetup() {
        widthToMoveBoard = offsetSide * 2 + boardWidth
        widthToMoveEntryBoard = width - boardWidth - offsetSide - boards[2].x
        hideLeftBoard()
        hideRightBoard()
    }

    private fun hideLeftBoard() {
        boards[0].moveBy(-widthToMoveBoard.toFloat(), 0f)
        val nRightBoardPegs = mastermindLeftBoardRows * mastermindBoardCols
        for(i in 0 until nRightBoardPegs) {
            displayedPegs[i].moveBy(-widthToMoveBoard.toFloat(), 0f)
            displayedScorePegs[i].moveBy(-widthToMoveBoard.toFloat(), 0f)
        }
    }

    private fun hideRightBoard() {
        boards[1].moveBy(widthToMoveBoard.toFloat(), 0f)
        val startIndex = mastermindLeftBoardRows * mastermindBoardCols
        val totalPegs = (mastermindRightBoardRows + mastermindLeftBoardRows) * mastermindBoardCols
        for(i in startIndex until totalPegs) {
            displayedPegs[i].moveBy(widthToMoveBoard.toFloat(), 0f)
            displayedScorePegs[i].moveBy(widthToMoveBoard.toFloat(), 0f)
        }
    }

    private fun buildBoards() {
        val remainingHeight = height - (height * percentOfHeightTopButtonsTake)
        boardsAreaHeight = remainingHeight * percentOfRemainingHeightTheBoardTakes
        buildSelectPegs()
        buildLeftBoard()
        buildLeftBoardPegs()
        buildRightBoard()
        buildRightBoardPegs()
        buildEntryBoard()
        buildEntryPegs()
        buildOptionPegs()
        buildSubmitEntryButton()
    }

    private fun buildSelectPegs() {
        val paint = Paint()
        paint.color = ContextCompat.getColor(context, R.color.lightBlue)

        pegSelection = GOval(selectedPegSize.toFloat(), selectedPegSize.toFloat())
        pegSelection.paint = paint
        add(pegSelection, width.toFloat() * 2, height.toFloat() * 2)

        optionSelection = GOval(selectedOptionPegSize.toFloat(), selectedOptionPegSize.toFloat())
        optionSelection.paint = paint
        add(optionSelection, width.toFloat() * 2, height.toFloat() * 2)
    }

    private fun addPegs(row: Int, col: Int,
                        pegStartX: Double, pegStartY: Double,
                        scorePegStartX: Double) {
        val paint = Paint()
        paint.color = ContextCompat.getColor(context, R.color.mediumGray)

        val peg = GOval(pegSize.toFloat(), pegSize.toFloat())
        peg.paint = paint
        displayedPegs.add(peg)
        add(displayedPegs.last(), (pegStartX + col * (pegSize + pegSpacing)).toFloat(),
            (pegStartY + row * (pegSize + displayedPegsVerticalSpacing)).toFloat())

        val scorePeg = GOval(scorePegSize.toFloat(), scorePegSize.toFloat())
        scorePeg.paint = paint
        displayedScorePegs.add(scorePeg)
        add(displayedScorePegs.last(),
            (scorePegStartX + col * (scorePegSize + scorePegSpacing)).toFloat(),
            (displayedPegs.last().y + pegSize + pegSpacing).toFloat())
    }

    private fun buildLeftBoard() {
        val leftBoard = GRect(boardWidth.toFloat(), boardsAreaHeight.toFloat())
        val paint = Paint()
        paint.color = Color.LTGRAY
        leftBoard.paint = paint
        boards.add(leftBoard)
        add(boards[0], offsetSide.toFloat(),
            (height - offsetSide - boardsAreaHeight - buttonHeight - elementSpacing).toFloat())
        boards[0].sendToBack()
        displayedPegsVerticalSpacing = (boards[0].height - mastermindLeftBoardRows * pegSize - 2 *
                pegOffsetTopOfBoard - scorePegSize - pegSpacing) / (mastermindLeftBoardRows - 1)
    }

    private fun buildLeftBoardPegs() {
        val pegsAreaWidth = pegSize * 4 + pegSpacing * 3
        val scorePegsAreaWidth = scorePegSize * 4 + scorePegSpacing * 3
        val pegStartX = boards[0].x + (boards[0].width - pegsAreaWidth) / 2
        val pegStartY = boards[0].y + pegOffsetTopOfBoard
        val scorePegStartX = boards[0].x + (boards[0].width - scorePegsAreaWidth) / 2
        for(row in 0 until mastermindLeftBoardRows)
            for(col in 0 until mastermindBoardCols)
                addPegs(row, col, pegStartX, pegStartY, scorePegStartX)
    }

    private fun buildRightBoard() {
        val rightBoardPegsAreaHeight =
            pegSize * 4 + displayedPegsVerticalSpacing * 3 + pegSpacing + scorePegSize
        val rightBoardHeight = pegOffsetTopOfBoard * 2 + rightBoardPegsAreaHeight
        val rightBoard = GRect(boardWidth.toFloat(), rightBoardHeight.toFloat())
        val paint = Paint()
        paint.color = Color.LTGRAY
        rightBoard.paint = paint
        boards.add(rightBoard)
        add(boards[1], (width - boardWidth - offsetSide).toFloat(), boards[0].y)
        boards[1].sendToBack()
    }

    private fun buildRightBoardPegs() {
        val pegsAreaWidth = pegSize * 4 + pegSpacing * 3
        val scorePegsAreaWidth = scorePegSize * 4 + scorePegSpacing * 3
        val pegStartX = boards[1].x + (boards[1].width - pegsAreaWidth) / 2
        val pegStartY = displayedPegs[0].y.toDouble()
        val scorePegStartX = boards[1].x + (boards[1].width - scorePegsAreaWidth) / 2
        for(row in 0 until mastermindRightBoardRows)
            for(col in 0 until mastermindBoardCols)
                addPegs(row, col, pegStartX, pegStartY, scorePegStartX)
    }

    private fun buildEntryBoard() {
        val clickableBoardHeight = boardsAreaHeight - boards[1].height - elementSpacing
        val clickableBoard = GRect(boardWidth.toFloat(), clickableBoardHeight.toFloat())
        val paint = Paint()
        paint.color = Color.LTGRAY
        clickableBoard.paint = paint
        boards.add(clickableBoard)
        add(boards[2], ((width - boardWidth) / 2).toFloat(),
            (height - offsetSide - boards[2].height - buttonHeight - elementSpacing).toFloat())
        boards[2].sendToBack()
    }

    private fun buildEntryPegs() {
        val pegsAreaWidth = pegSize * 4 + pegSpacing * 3
        val pegStartX = boards[2].x + (boards[2].width - pegsAreaWidth) / 2
        for(col in 0 until mastermindBoardCols) {
            val paint = Paint()
            paint.color = ContextCompat.getColor(context, R.color.mediumGray)

            val peg = GOval(pegSize.toFloat(), pegSize.toFloat())
            peg.paint = paint
            entryPegs.add(peg)
            add(entryPegs.last(), (pegStartX + col * (pegSize + pegSpacing)).toFloat(),
                (boards[2].y + pegOffsetTopOfBoard).toFloat())
        }
    }

    private fun buildOptionPegs() {
        val optionsAreaHeight = optionPegSize * 2 + optionPegSpacing
        val pegsStartY =
            entryPegs[0].bottomY + (boards[2].bottomY - entryPegs[0].bottomY - optionsAreaHeight) / 2
        var index = 0
        for(row in 0 until 2) {
            for(col in 0 until 3) {
                val paint = Paint()
                paint.color = getOptionColor(index++)

                val option = GOval(optionPegSize.toFloat(), optionPegSize.toFloat())
                option.paint = paint
                optionPegs.add(option)
                add(optionPegs.last(),
                    (entryPegs[0].x + col * (optionPegSize + optionPegSpacing)).toFloat(),
                    (pegsStartY + row * (optionPegSize + optionPegSpacing)).toFloat())
            }
        }
    }

    private fun getOptionColor(index: Int): Int {
        return when(index) {
            -1 -> ContextCompat.getColor(context, R.color.mediumGray)
            0 -> Color.BLACK
            1 -> Color.WHITE
            2 -> Color.RED
            3 -> Color.BLUE
            4 -> ContextCompat.getColor(context, R.color.green)
            else -> ContextCompat.getColor(context, R.color.orange)
        }
    }

    private fun buildSubmitEntryButton() {
        submitEntryButton = GSprite(resizeSubmitEntryButton(
            BitmapFactory.decodeResource(resources, R.drawable.submit_guess_button)))
        val optionsAreaHeight = optionPegSize * 2 + optionPegSpacing
        val remainingBoardWidth = boards[2].rightX - optionPegs[2].rightX
        add(submitEntryButton,
            optionPegs[2].rightX + (remainingBoardWidth - submitEntryButton.width) / 2,
            (optionPegs[0].y + (optionsAreaHeight - submitEntryButton.height) / 2).toFloat())
    }

    private fun resizeSubmitEntryButton(button: Bitmap): Bitmap {
        val optionsAreaHeight = optionPegSize * 2 + optionPegSpacing
        val submitButtonHeight = optionsAreaHeight * submitGuessButtonHeightPercentToOptionsArea
        val submitButtonWidth = button.width * (submitButtonHeight / button.height)
        return Bitmap.createScaledBitmap(
            button, submitButtonWidth.toInt(), submitButtonHeight.toInt(), false)
    }

    private fun initValues() {
        boardsAreaWidth = percentOfWidthTheBoardTakes * width
        boardWidth = (boardsAreaWidth - elementSpacing) / 2
        offsetSide = (width - boardsAreaWidth) / 2
        pegSize = boardWidth * pegPercentToBoardWidth
        optionPegSize = pegSize * optionPegPercentToPeg
        scorePegSize = pegSize * scorePegPercentToPeg
        pegSpacing = pegSize * pegSpacingPercentToRelativePeg
        pegOffsetTopOfBoard = pegSize * pegPercentOffsetTopOfBoard
        optionPegSpacing = optionPegSize * pegSpacingPercentToRelativePeg
        scorePegSpacing = scorePegSize * pegSpacingPercentToRelativePeg
        selectedPegSize = pegSize * selectedPegPercentToRelativePeg
        selectedOptionPegSize = optionPegSize * selectedPegPercentToRelativePeg
        for(i in 0 until mastermindBoardCols) {
            setPattern.add(-1)
            patternEntry.add(-1)
        }
    }

    private fun setOnscreenElements() {
        setMessagesBar()
        setMessagesBarArrows()
        setMenuButton()
        setRestartButton()
        setMessagesButton()
    }

    private fun setSizes() {
        fontSize = width / (1080.0 / fontSizeIn1080)
        elementSpacing = width / (1080.0 / elementSpacingIn1080)
    }

    private fun setMessagesBar() {
        val messagesBarSprite = GSprite(resizeMessagesBar(
            BitmapFactory.decodeResource(resources, R.drawable.messages_bar)))
        messagesBarWidth = messagesBarSprite.width.toDouble()
        messagesBar.add(messagesBarSprite)
        val messagesBarPaint = Paint()
        messagesBarPaint.color = ContextCompat.getColor(context, R.color.darkGray)
        messagesBarLabel = GLabel("")
        messagesBarLabel.paint =
            getButtonTextPaint(ContextCompat.getColor(context, R.color.darkGray))
        messagesBarLabel.fontSize = fontSize.toFloat()
        messagesBar.add(messagesBarLabel,
            (messagesBarSprite.width - messagesBarLabel.width) / 2,
            (buttonHeight - messagesBarLabel.height).toFloat() / 2)
        add(messagesBar, (width - boardsAreaWidth).toFloat() / 2,
            (height - offsetSide - buttonHeight).toFloat())
    }

    // messagesBarWidth = (A * (D - C)) / (A + B)
    // A = image width; B = image height; C = element spacing; D = board width
    private fun resizeMessagesBar(image: Bitmap): Bitmap {
        val messagesBarWidth = (image.width * (boardsAreaWidth - elementSpacing)) /
                (image.width + image.height)
        buttonHeight = image.height * (messagesBarWidth / image.width)
        return Bitmap.createScaledBitmap(
            image, messagesBarWidth.toInt(), buttonHeight.toInt(), false)
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
        add(menuButton, (width - buttonWidth).toFloat() / 2, offsetSide.toFloat())
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
        add(messagesButton, (messagesBar.x + boardsAreaWidth - messagesButton.width).toFloat(), messagesBar.y)
    }
}