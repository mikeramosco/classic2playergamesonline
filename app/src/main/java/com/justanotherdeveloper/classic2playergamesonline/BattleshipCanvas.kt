package com.justanotherdeveloper.classic2playergamesonline

import android.content.Context
import android.graphics.*
import android.os.CountDownTimer
import android.util.AttributeSet
import android.util.SparseArray
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.util.set
import stanford.androidlib.graphics.*
import java.util.*
import kotlin.collections.ArrayList

class BattleshipCanvas(context: Context, attrs: AttributeSet) : GCanvas(context, attrs),
    View.OnTouchListener, GestureDetector.OnGestureListener {

    // Gesture Detector
    private lateinit var mGestureDetector: GestureDetector

    // Battleship Activity
    lateinit var linkedActivity: BattleshipActivity

    // Players Moves During Turn
    private var playersMoves = ArrayList<String>()

    // Host Moves First Boolean
    var hostMovesFirst = false

    // Same Screen Option Boolean
    private var sameScreenOptionSelected = false

    // Host or Client Boolean
    var playerIsHost = false

    // Messages List
    var messages = ArrayList<String>()

    // Current Message Displayed
    private var displayedMessageIndex = 0

    // Players Turn Boolean - Use for Same Screen Option
    // for online, P1 represents player & P2 represents opponent (isP1sMove is always true)
    private var isP1sMove = true

    // Players Turn Boolean - User for Online Option
    // (not used for same screen option)
    private var isPlayersMove = false

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
        initSameScreenOptionObjectsSetup()
        delayShipSetup()
    }

    private fun delayShipSetup() {
        val prompt = linkedActivity.getString(
            R.string.playerSetupShipsPrompt,
            getPlayersTurnNumber(isP1sMove)
        )
        val dialogReadyButton = linkedActivity.blockScreenToChangePlayer(prompt)
        val timer = object : CountDownTimer(dialogDelay, dialogDelay) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                updateMessageBarText(prompt)
                initShipSetup()
                dialogReadyButton.isEnabled = true
            }
        }
        timer.start()
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
        updateMessageBarText(linkedActivity.getString(R.string.setupShipsOnlinePrompt))
        initShipSetup()
    }

    private fun firstMoveStart() {
        if (playerIsHost == hostMovesFirst) {
            isPlayersMove = true
            updateMessageBarText(linkedActivity.getString(R.string.playersFirstTurnOnlinePrompt))
        } else {
            isPlayersMove = false
            linkedActivity.waitForOpponentsMove()
            updateMessageBarText(linkedActivity.getString(R.string.opponentFirstTurnOnlinePrompt))
        }
    }

    fun getOpponentsMove(boardState: String): Boolean {
        val boardStateContents = boardState.split("\t")
        val nBoardStateIds = battleshipBoardLength * battleshipBoardLength * 2
        var moveIsHit = true
        if(boardStateContents.size > nBoardStateIds + 2) {
            val idOfPlayerWithNextTurn = boardStateContents[nBoardStateIds+1]
            val idOfPlayerWithLastMove = boardStateContents[nBoardStateIds+2]

            if(idOfPlayerWithLastMove.toInt() == linkedActivity.opponentId) {
                if(idOfPlayerWithNextTurn.toInt() == linkedActivity.playerId) {
                    isPlayersMove = true
                    moveIsHit = false
                    updateBoardMarkerImages(true)
                }
                var lastContent = boardStateContents.last()
                if(!lastContent.contains(":"))
                    lastContent = boardStateContents[boardStateContents.lastIndex - 1]
                initOpponentsMove(lastContent, moveIsHit)
            }
        }
        return !moveIsHit
    }

    private fun initOpponentsMove(opponentsMove: String, moveIsHit: Boolean) {
        val opponentsMoveLocation = opponentsMove.split(":")
        val row = opponentsMoveLocation[0].toInt()
        val col = opponentsMoveLocation[1].toInt()
        val markerImageIndex = if(moveIsHit) 1 else 3

        if(boardSquareNotPreviouslySelected(row, col, true)) {
            val message = if(moveIsHit)
                linkedActivity.getString(R.string.onlineHitText,
                    linkedActivity.getString(R.string.themText), getSquareAlphaNumber(row, col))
            else linkedActivity.getString(R.string.onlineMissText,
                linkedActivity.getString(R.string.themText), getSquareAlphaNumber(row, col),
                linkedActivity.getString(R.string.playersTurnOnlinePrompt))
            updateMessageBarText(message)

            boardMarkersP2.add(GSprite(boardMarkersImages[markerImageIndex]))
            boardIdArrayP1[row][col] += ".${boardMarkersP2.lastIndex}"
            when {
                animationInProgress -> {
                    val timer = object : CountDownTimer(listenerDelay, listenerDelay) {
                        override fun onTick(millisUntilFinished: Long) {}
                        override fun onFinish() {
                            when {
                                animationInProgress -> this.start()
                                displayingOpponentsBoard -> switchBoard(row, col)
                                else -> displayOpponentsMove(row, col)
                            }
                        }
                    }
                    timer.start()
                }
                displayingOpponentsBoard -> switchBoard(row, col)
                else -> displayOpponentsMove(row, col)
            }
        }
    }

    private fun displayOpponentsMove(row: Int, col: Int) {
        add(boardMarkersP2[boardMarkersP2.lastIndex],
            boardSquares[row][col].x, boardSquares[row][col].y)
        checkIfOpponentSunkPlayersShip(row, col)
    }

    private fun markPlayerShipDestroyed(shipId: String) {
        when (shipId) {
            carrierID -> battleshipsDestroyedP2[0] = true
            battleshipID -> battleshipsDestroyedP2[1] = true
            submarineID -> battleshipsDestroyedP2[2] = true
            largeShipID -> battleshipsDestroyedP2[3] = true
            tinyShipID -> battleshipsDestroyedP2[4] = true
        }
    }

    private fun checkIfOpponentSunkPlayersShip(row: Int, col: Int) {
        val squareId = boardIdArrayP1[row][col]
        val shipIndex = when {
            squareId.contains(carrierID) -> 0
            squareId.contains(battleshipID) -> 1
            squareId.contains(submarineID) -> 2
            squareId.contains(largeShipID) -> 3
            squareId.contains(tinyShipID) -> 4
            else -> return
        }
        val battleship = battleshipsP1[shipIndex]
        if(battleshipIsDestroyed(battleship.id, boardIdArrayP1)) {
            updateMessageBarText(linkedActivity.getString(R.string.onlineSunkShipText,
                linkedActivity.getString(R.string.theyText),
                linkedActivity.getString(R.string.yourText), getShipName(battleship.id)))
            battleship.sprite.bitmap = destroyedShipImages[shipIndex]
            if (!battleship.isVertical) battleship.sprite.bitmap =
                battleship.sprite.bitmap.rotate(90f)
            leftShipScores[shipIndex].bitmap = battleshipScoreImages[shipIndex]
            markPlayerShipDestroyed(battleship.id)
            removeHitMarkers(battleship, boardIdArrayP1, boardMarkersP2)
            checkIfGameIsOver(true)
        }
    }

    fun displayPlayersShipsToResumeStartingGame(boardState: String) {
        val boardStateContents = boardState.split("\t")
        setBoardIdArray(boardStateContents, 0, boardIdArrayP1)
        updateBattleships(true)
        displayPlayersBattleships()
        endSetupState()
    }

    private fun displayPlayersBattleships() {
        for(battleship in battleshipsP1) {
            if(!battleship.isVertical) battleship.sprite.bitmap =
                battleship.sprite.bitmap.rotate(90f)
            add(battleship.sprite)
            battleship.sprite.moveTo(
                boardSquares[battleship.startRow][battleship.startCol].x,
                boardSquares[battleship.startRow][battleship.startCol].y
            )
        }
    }

    private fun setBoardIdArray(boardStateContents: List<String>, startingIndex: Int,
                                boardIdArray: Array<Array<String>>) {
        var boardStateIndex = startingIndex
        for(row in 0 until battleshipBoardLength) {
            for(col in 0 until battleshipBoardLength) {
                boardIdArray[row][col] = boardStateContents[boardStateIndex++]
            }
        }
    }

    fun setupToResumeGame(boardState: String) {
        val boardStateContents = boardState.split("\t")
        val nBoardStateIds = battleshipBoardLength * battleshipBoardLength * 2
        setupBoards(boardStateContents)
        if(boardStateContents.size > nBoardStateIds + 2)
            setupMarkersToStart(boardStateContents)
        else firstMoveStart()
        endSetupState()
    }

    private fun setupMarkersToStart(boardStateContents: List<String>) {
        setupMarkers(true)
        setupMarkers(false)
        checkForSunkShips(true)
        checkForSunkShips(false)
        highlightRecentMoves(boardStateContents)
        getPlayerOfNextTurnToStart(boardStateContents)
    }

    private fun getPlayerOfNextTurnToStart(boardStateContents: List<String>) {
        val nBoardStateIds = battleshipBoardLength * battleshipBoardLength * 2
        val idOfPlayerWithNextTurn = boardStateContents[nBoardStateIds+1].toInt()
        isPlayersMove = linkedActivity.playerId == idOfPlayerWithNextTurn
        checkIfGameIsOver(true)
        if(!gameIsOver) displayLastMoveMessage(boardStateContents)
        if(!isPlayersMove) linkedActivity.waitForOpponentsMove()
    }

    private fun displayLastMoveMessage(boardStateContents: List<String>) {
        val nBoardStateIds = battleshipBoardLength * battleshipBoardLength * 2
        val idOfPlayerWithLastMove = boardStateContents[nBoardStateIds+2].toInt()
        val lastMoveContents =
            boardStateContents[boardStateContents.lastIndex].split(":")
        val playerHadLastMove = linkedActivity.playerId == idOfPlayerWithLastMove
        val row = lastMoveContents[0].toInt()
        val col = lastMoveContents[1].toInt()
        val boardIdArray = getBoardIdArray(!playerHadLastMove)
        val moveIsHit = !boardIdArray[row][col].contains(oceanID)

        val playerWithLastMove =
            if (playerHadLastMove) linkedActivity.getString(R.string.youText)
            else linkedActivity.getString(R.string.themText)
        val playerWithNextTurn =
            if (isPlayersMove) linkedActivity.getString(R.string.playersTurnOnlinePrompt)
            else linkedActivity.getString(R.string.opponentTurnOnlinePrompt)
        val message = if (moveIsHit) linkedActivity.getString(R.string.onlineHitTextOnReturn,
            playerWithLastMove, getSquareAlphaNumber(row, col), playerWithNextTurn)
        else linkedActivity.getString(R.string.onlineMissText,
            playerWithLastMove, getSquareAlphaNumber(row, col), playerWithNextTurn)
        updateMessageBarText(message)
    }

    private fun checkForSunkShips(forPlayer1: Boolean) {
        val boardIdArray = getBoardIdArray(forPlayer1)
        for((index, battleship) in getBattleships(forPlayer1).withIndex()) {
            if(battleshipIsDestroyed(battleship.id, boardIdArray)) {
                if(forPlayer1) {
                    battleship.sprite.bitmap = destroyedShipImages[index]
                    if (!battleship.isVertical) battleship.sprite.bitmap =
                        battleship.sprite.bitmap.rotate(90f)
                    leftShipScores[index].bitmap = battleshipScoreImages[index]
                    markPlayerShipDestroyed(battleship.id)
                    removeHitMarkers(battleship, boardIdArray, boardMarkersP2)
                } else {
                    val row = battleship.startRow
                    val col = battleship.startCol
                    if (!battleship.isVertical) destroyedShipsP1[index].bitmap =
                        destroyedShipsP1[index].bitmap.rotate(90f)
                    destroyedShipsP1[index].moveTo(boardSquares[row][col].x + width,
                        boardSquares[row][col].y)
                    rightShipScores[index].bitmap = flipImage(battleshipScoreImages[index])
                    markOpponentShipDestroyed(battleship.id)
                    removeHitMarkers(battleship, boardIdArray, boardMarkersP1)
                }
            }
        }
    }

    private fun highlightRecentMoves(boardStateContents: List<String>) {
        val nBoardStateIds = battleshipBoardLength * battleshipBoardLength * 2
        val lastContent = boardStateContents[boardStateContents.lastIndex]
        val idOfPlayerWithLastMove = boardStateContents[nBoardStateIds+2].toInt()
        val forPlayer1 = idOfPlayerWithLastMove == linkedActivity.playerId
        val boardMarkers = getOpponentsBoardMarkers(forPlayer1)
        var markerIndex = boardMarkers.lastIndex
        if(forPlayer1) markerIndex--
        var maxCount = boardStateContents.size
        if(!lastContent.contains(":")) maxCount--
        for(index in nBoardStateIds+3 until maxCount) {
            val marker = boardMarkers[markerIndex--]
            if (marker.bitmap == boardMarkersImages[2]) marker.bitmap = boardMarkersImages[1]
            else if (marker.bitmap == boardMarkersImages[4]) marker.bitmap = boardMarkersImages[3]
        }
    }

    private fun setupMarkers(forPlayer1: Boolean) {
        val boardIdArray = getBoardIdArray(!forPlayer1)
        val boardMarkers = getOpponentsBoardMarkers(forPlayer1)
        val markerLocations = SparseArray<String>()
        var nMarkers = 0
        for(row in 0 until battleshipBoardLength) {
            for(col in 0 until battleshipBoardLength) {
                val markerIndexString = extractNumber(boardIdArray[row][col])
                if(markerIndexString != "") {
                    val markerIndex = markerIndexString.toInt()
                    markerLocations[markerIndex] = "$row:$col"
                    markerLocations[markerIndex] +=
                        if(boardIdArray[row][col].contains(oceanID)) ":miss" else ":hit"
                    nMarkers++
                }
            }
        }
        if(!forPlayer1 && nMarkers > 0) {
            boardMarkers.add(GSprite(boardMarkersImages[0]))
            add(boardMarkers[boardMarkers.lastIndex])
            moveElementOffscreen(boardMarkers[boardMarkers.lastIndex])
        }
        for(index in 0 until nMarkers) {
            val markerLocationContents = markerLocations[index].split(":")
            val row = markerLocationContents[0].toInt()
            val col = markerLocationContents[1].toInt()
            val type = markerLocationContents[2]
            val boardMarker = boardMarkers[boardMarkers.lastIndex]
            boardMarker.bitmap = if(type == "hit") boardMarkersImages[2] else boardMarkersImages[4]
            boardMarker.moveTo(boardSquares[row][col].x, boardSquares[row][col].y)
            if(forPlayer1) boardMarker.moveBy(width.toFloat(), 0f)
            if(index < nMarkers - 1 || forPlayer1) {
                boardMarkers.add(GSprite(boardMarkersImages[0]))
                add(boardMarkers[boardMarkers.lastIndex])
                moveElementOffscreen(boardMarkers[boardMarkers.lastIndex])
            }
        }
    }

    private fun setupBoards(boardStateContents: List<String>) {
        val playerStartingIndex = if(playerIsHost) 0 else battleshipBoardLength * battleshipBoardLength
        val opponentStartingIndex = if(!playerIsHost) 0 else battleshipBoardLength * battleshipBoardLength
        setBoardIdArray(boardStateContents, playerStartingIndex, boardIdArrayP1)
        setBoardIdArray(boardStateContents, opponentStartingIndex, boardIdArrayP2)
        initBattleshipsP2()
        updateBattleships(true)
        updateBattleships(false)
        displayPlayersBattleships()
    }

    fun updateOpponentsBoardState(boardState: String) {
        val boardStateContents = boardState.split("\t")
        val startingIndex = if(!playerIsHost) 0 else battleshipBoardLength * battleshipBoardLength
        setBoardIdArray(boardStateContents, startingIndex, boardIdArrayP2)
        initBattleshipsP2()
        updateBattleships(false)
        firstMoveStart()
    }

    private fun updateBattleships(forPlayer1: Boolean){
        val boardIdArray = getBoardIdArray(forPlayer1)
        for(battleship in getBattleships(forPlayer1))
            for(row in 0 until battleshipBoardLength) {
                var battleshipFound = false
                for(col in 0 until battleshipBoardLength) {
                    val squareId = boardIdArray[row][col]
                    if(squareId.contains("${battleship.id}$horizontalID") ||
                        squareId.contains("${battleship.id}$verticalID")) {
                        if(squareId.contains(horizontalID))
                            battleship.isVertical = false
                        moveBattleshipToPlacement(battleship, row, col, boardIdArray, false)
                        battleshipFound = true
                        break
                    }
                }
                if(battleshipFound) break
            }
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
        logBoard(true)
        logBoard(false)
        return false
    }

    private fun runOnlineOptionFunctions(motionEvent: MotionEvent) {
        if(gameIsReady && !actionInProgress && !animationInProgress) {
            checkIfBattleshipSelected(motionEvent)
            checkIfButtonsClickedForOnlineOption(motionEvent)
            checkIfBoardClicked(motionEvent, true)
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
            rotateButton -> attemptToRotateBattleship(selectedBattleship.centerRow,
                selectedBattleship.centerCol, getBoardIdArray(isP1sMove))
            restartButton -> {
                preventButtonClickUntilDialogAppears()
                linkedActivity.restartOnlineGameButtonClicked()
            }
            switchBoardButton -> switchBoard()
            prevMessageButton -> displayPreviousMessage()
            nextMessageButton -> displayNextMessage()
            readyButton -> onlineOptionReadyButtonClicked()
            boardMarkersP1[boardMarkersP1.lastIndex] -> markBoard(true)
        }
    }

    fun restartOnlineGame() {
        if (displayingOpponentsBoard)
            switchBoardButton.bitmap = switchBoardButton.bitmap.rotate(180f)
        moveElementOffscreen(switchBoardButton)
        displayReadyAndRotateButton()
        displayingOpponentsBoard = false
        isPlayersMove = false
        gameIsOver = false
        gameIsReady = false
        playerIsSettingUp = true
        hideElement(boards[0])
        hideElement(boards[1])
        showElement(boards[0])
        for (marker in boardMarkersP1) remove(marker)
        for (marker in boardMarkersP2) remove(marker)
        boardMarkersP1 = ArrayList()
        boardMarkersP2 = ArrayList()
        boardMarkersP1.add(GSprite(boardMarkersImages[0]))
        add(boardMarkersP1[0])
        moveElementOffscreen(boardMarkersP1[0])
        for (index in 0 until 5) {
            battleshipsDestroyedP1[index] = false
            battleshipsDestroyedP2[index] = false
            destroyedShipsP1[index].bitmap = destroyedShipImages[index]
            moveElementOffscreen(destroyedShipsP1[index])
            val p1Battleship = battleshipsP1[index]
            p1Battleship.sprite.bitmap = battleshipImages[index]
            p1Battleship.isVertical = true
            remove(p1Battleship.sprite)
            if(battleshipsP2.size > index) {
                val p2Battleship = battleshipsP2[index]
                p2Battleship.sprite.bitmap = battleshipImages[index]
                p2Battleship.isVertical = true
                remove(p2Battleship.sprite)
            }
        }
        boardIdArrayP1 = Array(battleshipBoardLength) { Array(battleshipBoardLength) { oceanID } }
        boardIdArrayP2 = Array(battleshipBoardLength) { Array(battleshipBoardLength) { oceanID } }
        playersMoves = ArrayList()
        updateShipScoreImages()
        updateMessageBarText("")
        linkedActivity.displayAppropriateDialogForRestart()
    }

    private fun onlineOptionReadyButtonClicked() {
        endSetupState()
        unselectBattleship()
        linkedActivity.notifyFirebaseThatShipsAreSetup(getBoardStateAsString(boardIdArrayP1))
    }

    private fun endSetupState() {
        gameIsReady = true
        playerIsSettingUp = false
        displaySwitchBoardButton()
        moveElementOffscreen(readyButton)
        moveElementOffscreen(rotateButton)
    }

    private fun getBoardStateAsString(boardIdArray: Array<Array<String>>): String {
        var boardState = ""
        for(row in 0 until battleshipBoardLength) {
            for(col in 0 until battleshipBoardLength) {
                boardState += boardIdArray[row][col]
                if(row != battleshipBoardLength -1 || col != battleshipBoardLength -1)
                    boardState += "\t"
            }
        }
        return boardState
    }

    private fun runSameScreenFunctions(motionEvent: MotionEvent) {
        if (gameIsReady && !actionInProgress && !animationInProgress) {
            checkIfBattleshipSelected(motionEvent)
            checkIfButtonsClickedForSameScreenOption(motionEvent)
            checkIfBoardClicked(motionEvent)
        }
    }

    private fun checkIfBattleshipSelected(motionEvent: MotionEvent) {
        if (playerIsSettingUp) {
            val battleships = getBattleships(isP1sMove)
            for (index in 0 until 5)
                if (getElementAt(GPoint(motionEvent.x, motionEvent.y)) ==
                    battleships[index].sprite
                ) {
                    if (selectedBattleship != battleships[index])
                        selectBattleship(index, battleships)
                }
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
            rotateButton -> attemptToRotateBattleship(selectedBattleship.centerRow,
                selectedBattleship.centerCol, getBoardIdArray(isP1sMove))
            restartButton -> {
                preventButtonClickUntilDialogAppears()
                linkedActivity.sameScreenRestartClicked()
            }
            switchBoardButton -> switchBoard()
            prevMessageButton -> displayPreviousMessage()
            nextMessageButton -> displayNextMessage()
            readyButton -> {
                preventButtonClickUntilDialogAppears()
                if (isP1sMove) setupForP2Setup()
                else setupForGameplay()
            }
            boardMarkersP1[boardMarkersP1.lastIndex] -> if (isP1sMove) markBoard()
            boardMarkersP2[boardMarkersP2.lastIndex] -> if (!isP1sMove) markBoard()
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

    private fun attemptToRotateBattleship(row: Int, col: Int, boardIdArray: Array<Array<String>>,
                                          isFirstCall: Boolean = true): Boolean {
        selectedBattleship.isVertical = !selectedBattleship.isVertical
        var shipRotated =
            if (isValidBattleshipPlacement(selectedBattleship, row, col, boardIdArray)) {
                rotateBattleship(row, col, boardIdArray)
                true
            } else checkIfRotatableWithinItsRowOrCol(row, col, boardIdArray)
        if(!shipRotated && isFirstCall) shipRotated = checkForOpeningToRotate(row, col, boardIdArray)
        if(!shipRotated) selectedBattleship.isVertical = !selectedBattleship.isVertical
        return shipRotated
    }

    private fun rotateBattleship(row: Int, col: Int, boardIdArray: Array<Array<String>>) {
        val degreesToRotate = if(selectedBattleship.isVertical) -90f else 90f
        selectedBattleship.isVertical = !selectedBattleship.isVertical
        selectedBattleship.sprite.bitmap = selectedBattleship.sprite.bitmap.rotate(degreesToRotate)
        removeBattleshipFromBoardIdArray(selectedBattleship, boardIdArray)
        selectedBattleship.isVertical = !selectedBattleship.isVertical
        moveBattleshipToPlacement(selectedBattleship, row, col, boardIdArray)
    }

    private fun checkIfRotatableWithinItsRowOrCol(row: Int, col: Int, boardIdArray: Array<Array<String>>): Boolean {
        var checkRow = row
        var checkCol = col
        var spacesChecked = 0
        var distanceFromCenter = 0
        while(spacesChecked++ < selectedBattleship.length) {
            if(distanceFromCenter >= 0) distanceFromCenter++
            distanceFromCenter = -distanceFromCenter
            if(selectedBattleship.isVertical)
                checkRow = row + distanceFromCenter
            else checkCol = col + distanceFromCenter
            if(isValidBattleshipPlacement(selectedBattleship, checkRow, checkCol, boardIdArray)) {
                rotateBattleship(checkRow, checkCol, boardIdArray)
                return true
            }
        }
        return false
    }

    private fun checkForOpeningToRotate(row: Int, col: Int, boardIdArray: Array<Array<String>>): Boolean {
        var shipRotated = false
        selectedBattleship.isVertical = !selectedBattleship.isVertical
        var checkRow = row
        var checkCol = col
        var spacesChecked = 0
        var distanceFromCenter = 0
        var backDirectionBlocked = false
        var forwardDirectionBlocked = false
        while(spacesChecked++ < battleshipBoardLength * 2) {
            if(forwardDirectionBlocked && backDirectionBlocked) break
            if(distanceFromCenter >= 0) distanceFromCenter++
            distanceFromCenter = -distanceFromCenter
            if(selectedBattleship.isVertical)
                checkRow = row + distanceFromCenter
            else checkCol = col + distanceFromCenter
            val directionCheckedBlocked =
                if(distanceFromCenter > 0) forwardDirectionBlocked else backDirectionBlocked
            if(checkRow < 0 || checkRow >= battleshipBoardLength ||
                checkCol < 0 || checkCol >= battleshipBoardLength || directionCheckedBlocked) {
                if(distanceFromCenter > 0)
                    forwardDirectionBlocked = true
                else backDirectionBlocked = true
            } else if(!boardIdArray[checkRow][checkCol].contains(oceanID) &&
                !boardIdArray[checkRow][checkCol].contains(selectedBattleship.id)) {
                if(distanceFromCenter > 0)
                    forwardDirectionBlocked = true
                else backDirectionBlocked = true
            } else if(attemptToRotateBattleship(checkRow, checkCol, boardIdArray, false)) {
                shipRotated = true
                break
            }
        }
        if(!shipRotated) selectedBattleship.isVertical = !selectedBattleship.isVertical
        return shipRotated
    }

    private fun removeBattleshipFromBoardIdArray(battleship: Battleship,
                                                 boardIdArray: Array<Array<String>>) {
        if (battleship.isVertical) for (index in 0 until battleship.length)
            boardIdArray[battleship.startRow + index][battleship.startCol] = oceanID
        else for (index in 0 until battleship.length)
            boardIdArray[battleship.startRow][battleship.startCol + index] = oceanID
    }

    private fun switchBoard(row: Int = -1, col: Int = -1) {
        var velocityX = width.toFloat() / battleshipAnimateFrames.toFloat()
        if (displayingOpponentsBoard) clearSelectedSquare(getOpponentsBoardMarkers(isP1sMove))
        else velocityX = -velocityX
        displayingOpponentsBoard = !displayingOpponentsBoard
        var count = 1
        animationInProgress = true
        animate(battleshipFPS) {
            if (count <= battleshipAnimateFrames) {
                for (battleship in getBattleships(isP1sMove)) battleship.sprite.moveBy(
                    velocityX,
                    0f
                )
                if(gameIsOver && !sameScreenOptionSelected)
                    for (battleship in battleshipsP2) battleship.sprite.moveBy(
                        velocityX,
                        0f
                    )
                for (battleship in getDestroyedShips(isP1sMove))
                    battleship.moveBy(velocityX, 0f)
                boards[0].moveBy(velocityX, 0f)
                boards[1].moveBy(velocityX, 0f)
                for (marker in getOpponentsBoardMarkers(!isP1sMove))
                    marker.moveBy(velocityX, 0f)
                for (marker in getOpponentsBoardMarkers(isP1sMove))
                    marker.moveBy(velocityX, 0f)
                if (count++ == battleshipAnimateFrames) {
                    moveSwitchBoardButton()
                    animationInProgress = false
                    animationStop()
                    if(row != -1)
                        displayOpponentsMove(row, col)
                }
            }
        }
    }

    private fun moveSwitchBoardButton() {
        switchBoardButton.bitmap = switchBoardButton.bitmap.rotate(180f)
        if (displayingOpponentsBoard) switchBoardButton.moveTo(messagesBar.x, switchBoardButton.y)
        else displaySwitchBoardButton()
    }

    private fun clearSelectedSquare(opponentsBoardMarkers: ArrayList<GSprite>) {
        moveElementOffscreen(opponentsBoardMarkers[opponentsBoardMarkers.lastIndex])
        selectedRow = -1; selectedCol = -1
    }

    private fun setupForP2Setup() {
        isP1sMove = false
        val prompt = linkedActivity.getString(
            R.string.playerSetupShipsPrompt,
            getPlayersTurnNumber(isP1sMove)
        )
        val dialogReadyButton = linkedActivity.blockScreenToChangePlayer(prompt)
        val timer = object : CountDownTimer(dialogDelay, dialogDelay) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                updateMessageBarText(prompt)
                for (battleship in battleshipsP1) hideElement(battleship.sprite)
                moveBattleshipsToRandomLocation(battleshipsP2, boardIdArrayP2)
                selectBattleship(0, battleshipsP2)
                dialogReadyButton.isEnabled = true
            }
        }
        timer.start()
    }

    private fun setupForGameplay() {
        isP1sMove = true
        val prompt = linkedActivity.getString(
            R.string.playerMovesNextPrompt,
            getPlayersTurnNumber(isP1sMove)
        )
        val dialogReadyButton = linkedActivity.blockScreenToChangePlayer(prompt)
        val timer = object : CountDownTimer(dialogDelay, dialogDelay) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                displaySwitchBoardButton()
                updateMessageBarText(prompt)
                playerIsSettingUp = false
                moveElementOffscreen(readyButton)
                moveElementOffscreen(rotateButton)
                unselectBattleship()
                for (battleship in battleshipsP2) hideElement(battleship.sprite)
                for (battleship in battleshipsP1) showElement(battleship.sprite)
                dialogReadyButton.isEnabled = true
            }
        }
        timer.start()
    }

    private fun displaySwitchBoardButton() {
        if (displayingOpponentsBoard) switchBoardButton.bitmap =
            switchBoardButton.bitmap.rotate(180f)
        switchBoardButton.moveTo(
            width - messagesBar.x - switchBoardButton.width,
            (boardSquares[0][0].y - buttonHeight.toFloat() - elementSpacing).toFloat()
        )
    }

    fun initRestartSameScreenGame() {
        val prompt = linkedActivity.getString(
            R.string.playerSetupShipsPrompt, "1")
        val dialogReadyButton = linkedActivity.blockScreenToChangePlayer(prompt)
        val timer = object : CountDownTimer(dialogDelay, dialogDelay) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                restartSameScreenGame(prompt)
                dialogReadyButton.isEnabled = true
            }
        }
        timer.start()
    }

    private fun restartSameScreenGame(prompt: String) {
        if (displayingOpponentsBoard)
            switchBoardButton.bitmap = switchBoardButton.bitmap.rotate(180f)
        moveElementOffscreen(switchBoardButton)
        displayReadyAndRotateButton()
        displayingOpponentsBoard = false
        isP1sMove = true
        gameIsOver = false
        playerIsSettingUp = true
        hideElement(boards[0])
        hideElement(boards[1])
        showElement(boards[0])
        for (marker in boardMarkersP1) remove(marker)
        for (marker in boardMarkersP2) remove(marker)
        boardMarkersP1 = ArrayList()
        boardMarkersP2 = ArrayList()
        boardMarkersP1.add(GSprite(boardMarkersImages[0]))
        boardMarkersP2.add(GSprite(boardMarkersImages[0]))
        add(boardMarkersP1[0])
        add(boardMarkersP2[0])
        moveElementOffscreen(boardMarkersP1[0])
        moveElementOffscreen(boardMarkersP2[0])
        for (index in 0 until 5) {
            battleshipsDestroyedP1[index] = false
            battleshipsDestroyedP2[index] = false
            destroyedShipsP1[index].bitmap = destroyedShipImages[index]
            destroyedShipsP2[index].bitmap = destroyedShipImages[index]
            moveElementOffscreen(destroyedShipsP1[index])
            moveElementOffscreen(destroyedShipsP2[index])
            val p1Battleship = battleshipsP1[index]
            val p2Battleship = battleshipsP2[index]
            p1Battleship.sprite.bitmap = battleshipImages[index]
            p2Battleship.sprite.bitmap = battleshipImages[index]
            p1Battleship.isVertical = true
            p2Battleship.isVertical = true
            remove(p1Battleship.sprite)
            remove(p2Battleship.sprite)
        }
        boardIdArrayP1 = Array(battleshipBoardLength) { Array(battleshipBoardLength) { oceanID } }
        boardIdArrayP2 = Array(battleshipBoardLength) { Array(battleshipBoardLength) { oceanID } }
        updateShipScoreImages()
        updateMessageBarText(prompt)
        initShipSetup()
    }

    private fun displayReadyAndRotateButton() {
        val readyButtonAreaWidth = buttonWidth + elementSpacing + buttonHeight
        readyButton.moveTo(
            (width - readyButtonAreaWidth).toFloat() / 2,
            (boardSquares[0][0].y - buttonHeight.toFloat() - elementSpacing).toFloat()
        )
        rotateButton.moveTo((readyButton.x + buttonWidth + elementSpacing).toFloat(), readyButton.y)
    }

    private fun getBoardStateToUpdateFirebase(): String {
        var boardState = if(playerIsHost)
            getBoardStateAsString(boardIdArrayP1) + "\t" + getBoardStateAsString(boardIdArrayP2)
        else getBoardStateAsString(boardIdArrayP2) + "\t" + getBoardStateAsString(boardIdArrayP1)
        boardState += "\t" + getDateString()
        boardState += if(isPlayersMove) "\t" + linkedActivity.playerId else "\t" + linkedActivity.opponentId
        boardState += "\t" + linkedActivity.playerId
        for(move in playersMoves) boardState += "\t" + move
        if(gameIsOver) boardState += "\t" + linkedActivity.playerId
        return boardState
    }

    private fun markBoard(forOnline: Boolean = false) {
        val opponentsBoardMarkers = getOpponentsBoardMarkers(isP1sMove)
        val opponentsBoardIdArray = getBoardIdArray(!isP1sMove)
        opponentsBoardIdArray[selectedRow][selectedCol] += ".${opponentsBoardMarkers.lastIndex}"
        val targetMissed = opponentsBoardIdArray[selectedRow][selectedCol].contains(oceanID)
        val markerImageIndex = if (targetMissed) 3 else 1

        opponentsBoardMarkers[opponentsBoardMarkers.lastIndex].bitmap =
            boardMarkersImages[markerImageIndex]
        opponentsBoardMarkers[opponentsBoardMarkers.lastIndex].sendToFront()
        opponentsBoardMarkers.add(GSprite(boardMarkersImages[0]))
        add(opponentsBoardMarkers[opponentsBoardMarkers.lastIndex])
        moveElementOffscreen(opponentsBoardMarkers[opponentsBoardMarkers.lastIndex])
        val squareAlphaNumber = getSquareAlphaNumber()

        if (forOnline) {
            if(targetMissed) {
                isPlayersMove = false
                linkedActivity.waitForOpponentsMove()
                updateBoardMarkerImages(false)
            }
            playersMoves.add("$selectedRow:$selectedCol")
        }

        val message: String
        if (targetMissed) {
            message = if (forOnline) linkedActivity.getString(
                R.string.onlineMissText,
                linkedActivity.getString(R.string.youText), squareAlphaNumber,
                linkedActivity.getString(R.string.opponentTurnOnlinePrompt)
            )
            else {
                delayChangeTurn()
                linkedActivity.getString(
                    R.string.missText,
                    getPlayersTurnNumber(isP1sMove), squareAlphaNumber
                )
            }
            updateMessageBarText(message)
        } else {
            message = if (forOnline) linkedActivity.getString(R.string.onlineHitText,
                linkedActivity.getString(R.string.youText), squareAlphaNumber)
            else linkedActivity.getString(R.string.hitText,
                getPlayersTurnNumber(isP1sMove), squareAlphaNumber)
            updateMessageBarText(message)
            checkIfBattleshipDestroyed(forOnline)
        }
        if(forOnline) {
            linkedActivity.updateBoardStateInFirebase(getBoardStateToUpdateFirebase())
            if(targetMissed) playersMoves = ArrayList()
        }
    }

    private fun delayChangeTurn() {
        actionInProgress = true
        val timer = object : CountDownTimer(turnChangeDelay, turnChangeDelay) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                changeTurn()
            }
        }
        timer.start()
    }

    private fun getSquareAlphaNumber(row: Int = selectedRow, col: Int = selectedCol): String {
        val alpha = when (col) {
            0 -> "A"; 1 -> "B"; 2 -> "C"; 3 -> "D"; 4 -> "E"
            5 -> "F"; 6 -> "G"; 7 -> "H"; 8 -> "I"; else -> "J"
        }
        return "$alpha${row + 1}"
    }

    private fun changeTurn() {
        isP1sMove = !isP1sMove
        val prompt =
            linkedActivity.getString(
                R.string.playerMovesNextPrompt, getPlayersTurnNumber(isP1sMove)
            )
        val dialogReadyButton = linkedActivity.blockScreenToChangePlayer(prompt)
        val timer = object : CountDownTimer(dialogDelay, dialogDelay) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                displaySwitchBoardButton()
                updateMessageBarText(prompt)
                updateBoardMarkerImages()
                updateShipScoreImages()
                hidePreviousPlayersElements()
                showCurrentPlayersElements()
                actionInProgress = false
                dialogReadyButton.isEnabled = true
            }
        }
        timer.start()
    }

    private fun updateBoardMarkerImages(forThisPlayer: Boolean = isP1sMove) {
        for (marker in getOpponentsBoardMarkers(forThisPlayer))
            if (marker.bitmap == boardMarkersImages[1]) marker.bitmap = boardMarkersImages[2]
            else if (marker.bitmap == boardMarkersImages[3]) marker.bitmap = boardMarkersImages[4]
    }

    private fun updateShipScoreImages() {
        val shipScoresP1 = getShipScores(isP1sMove)
        val shipScoresP2 = getShipScores(!isP1sMove)

        for (i in 0 until 5) {
            if (battleshipsDestroyedP1[i]) shipScoresP1[i].bitmap = battleshipScoreImages[i]
            else shipScoresP1[i].bitmap = battleshipNoScoreImages[i]
            if(isP1sMove) shipScoresP1[i].bitmap = flipImage(shipScoresP1[i].bitmap)
            if (battleshipsDestroyedP2[i]) shipScoresP2[i].bitmap = battleshipScoreImages[i]
            else shipScoresP2[i].bitmap = battleshipNoScoreImages[i]
            if(!isP1sMove) shipScoresP2[i].bitmap = flipImage(shipScoresP2[i].bitmap)
        }
    }

    private fun hidePreviousPlayersElements() {
        for (battleship in getBattleships(!isP1sMove))
            hideElement(battleship.sprite)
        for (battleship in getDestroyedShips(!isP1sMove))
            hideElement(battleship)
        hideElement(boards[0])
        hideElement(boards[1])
        for (marker in getOpponentsBoardMarkers(isP1sMove))
            hideElement(marker)
    }

    private fun showCurrentPlayersElements() {
        for (battleship in getBattleships(isP1sMove))
            showElement(battleship.sprite)
        showElement(boards[0])
        for (marker in getOpponentsBoardMarkers(!isP1sMove))
            showElement(marker)
        displayingOpponentsBoard = false
    }

    private fun checkIfBattleshipDestroyed(forOnline: Boolean) {
        val opponentsBoardIdArray = getBoardIdArray(!isP1sMove)
        val squareId = opponentsBoardIdArray[selectedRow][selectedCol]
        val shipIndex = when {
            squareId.contains(carrierID) -> 0
            squareId.contains(battleshipID) -> 1
            squareId.contains(submarineID) -> 2
            squareId.contains(largeShipID) -> 3
            squareId.contains(tinyShipID) -> 4
            else -> 0
        }
        val destroyedShips = getDestroyedShips(isP1sMove)
        val battleship = getBattleships(!isP1sMove)[shipIndex]
        if (battleshipIsDestroyed(battleship.id, opponentsBoardIdArray)) {
            val message = if(forOnline) linkedActivity.getString(R.string.onlineSunkShipText,
                linkedActivity.getString(R.string.youText), linkedActivity.getString(R.string.theirText),
                getShipName(battleship.id))
            else linkedActivity.getString(R.string.sunkShipText, getPlayersTurnNumber(isP1sMove),
                getPlayersTurnNumber(!isP1sMove), getShipName(battleship.id))
            updateMessageBarText(message)
            val row = battleship.startRow
            val col = battleship.startCol
            if(!forOnline) {
                battleship.sprite.bitmap = destroyedShipImages[shipIndex]
                if (!battleship.isVertical) battleship.sprite.bitmap =
                    battleship.sprite.bitmap.rotate(90f)
            }
            if (!battleship.isVertical) destroyedShips[shipIndex].bitmap =
                destroyedShips[shipIndex].bitmap.rotate(90f)
            removeHitMarkers(battleship, opponentsBoardIdArray, getOpponentsBoardMarkers(isP1sMove))
            destroyedShips[shipIndex].moveTo(boardSquares[row][col].x, boardSquares[row][col].y)
            rightShipScores[shipIndex].bitmap = flipImage(battleshipScoreImages[shipIndex])
            markOpponentShipDestroyed(battleship.id)
            checkIfGameIsOver(forOnline)
        }
    }

    private fun battleshipIsDestroyed(shipId: String, boardIdArray: Array<Array<String>>): Boolean {
        for (row in 0 until battleshipBoardLength)
            for (col in 0 until battleshipBoardLength) {
                val squareId = boardIdArray[row][col]
                if (squareId == shipId ||
                    squareId == "$shipId$horizontalID" ||
                    squareId == "$shipId$verticalID")
                    return false
            }
        return true
    }

    private fun getShipName(shipId: String): String {
        return when (shipId) {
            carrierID -> linkedActivity.getString(R.string.carrierText)
            battleshipID -> linkedActivity.getString(R.string.battleshipBoatText)
            submarineID -> linkedActivity.getString(R.string.submarineText)
            largeShipID -> linkedActivity.getString(R.string.largeShipText)
            else -> linkedActivity.getString(R.string.tinyShipText)
        }
    }

    private fun removeHitMarkers(
        battleship: Battleship,
        boardIdArray: Array<Array<String>>,
        opponentsBoardMarkers: ArrayList<GSprite>
    ) {
        for(index in 0 until battleship.length) {
            val markerIndex = if(battleship.isVertical)
                extractNumber(boardIdArray[battleship.startRow + index][battleship.startCol]).toInt()
            else extractNumber(boardIdArray[battleship.startRow][battleship.startCol + index]).toInt()
            moveElementOffscreen(opponentsBoardMarkers[markerIndex])
        }
    }

    private fun markOpponentShipDestroyed(shipId: String) {
        val battleshipsDestroyed = getDestroyedShipsBooleans(isP1sMove)
        when (shipId) {
            carrierID -> battleshipsDestroyed[0] = true
            battleshipID -> battleshipsDestroyed[1] = true
            submarineID -> battleshipsDestroyed[2] = true
            largeShipID -> battleshipsDestroyed[3] = true
            tinyShipID -> battleshipsDestroyed[4] = true
        }
    }

    private fun extractNumber(str: String): String {
        val sb = StringBuilder()
        var found = false
        for (c in str.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c)
                found = true
            } else if (found) {
                break
            }
        }
        return sb.toString()
    }

    private fun checkIfGameIsOver(forOnline: Boolean) {
        var gameIsOver = true
        val forPlayer1 = if(forOnline) isPlayersMove else isP1sMove
        for (battleshipIsDestroyed in getDestroyedShipsBooleans(forPlayer1))
            if (!battleshipIsDestroyed) gameIsOver = false
        if (gameIsOver) {
            val message = if(forOnline && isPlayersMove)
                linkedActivity.getString(R.string.onlineGameOverText,
                    linkedActivity.getString(R.string.youText))
            else if(forOnline && !isPlayersMove)
                linkedActivity.getString(R.string.onlineGameOverText,
                    linkedActivity.getString(R.string.theyText))
            else linkedActivity.getString(R.string.gameOverText,
                getPlayersTurnNumber(isP1sMove))
            updateMessageBarText(message)
            if(forOnline && !isPlayersMove) revealOpponentsRemainingShips()
            this.gameIsOver = true
        }
    }

    private fun revealOpponentsRemainingShips() {
        for((index, battleshipDestroyed ) in battleshipsDestroyedP1.withIndex()) {
            if(!battleshipDestroyed) {
                val battleship = battleshipsP2[index]
                battleship.sprite.bitmap = battleshipImages[index]
                if(!battleship.isVertical)
                    battleship.sprite.bitmap = battleship.sprite.bitmap.rotate(90f)
                val row = battleship.startRow
                val col = battleship.startCol
                add(battleship.sprite, boardSquares[row][col].x + width, boardSquares[row][col].y)
            }
        }
        for(marker in boardMarkersP1) marker.sendToFront()
    }

    private fun checkIfBoardClicked(motionEvent: MotionEvent, forOnline: Boolean = false) {
        if (gameIsOver) return
        for (row in 0 until battleshipBoardLength)
            for (col in 0 until battleshipBoardLength)
                if (getElementAt(GPoint(motionEvent.x, motionEvent.y)) ==
                    boardSquares[row][col]
                ) {
                    if (playerIsSettingUp)
                        moveSelectedBattleship(row, col)
                    else if (displayingOpponentsBoard)
                        if(!forOnline || forOnline && isPlayersMove)
                            if(boardSquareNotPreviouslySelected(row, col))
                                selectBoardSquare(row, col)
                }
    }

    private fun boardSquareNotPreviouslySelected(row: Int, col: Int,
                                                 forPlayersBoard: Boolean = !isP1sMove): Boolean {
        return extractNumber(getBoardIdArray(forPlayersBoard)[row][col]) == ""
    }

    private fun moveSelectedBattleship(row: Int, col: Int) {
        val boardIdArray = getBoardIdArray(isP1sMove)
        var battleshipMoved = false
        if (selectedBattleship.length > 0) {
            if(isValidBattleshipPlacement(selectedBattleship, row, col, boardIdArray)) {
                removeBattleshipFromBoardIdArray(selectedBattleship, boardIdArray)
                moveBattleshipToPlacement(selectedBattleship, row, col, boardIdArray)
                battleshipMoved = true
            } else {
                var checkRow = row
                var checkCol = col
                var spacesChecked = 0
                var distanceFromCenter = 0
                while(spacesChecked++ < selectedBattleship.length) {
                    if(distanceFromCenter >= 0) distanceFromCenter++
                    distanceFromCenter = -distanceFromCenter
                    if(selectedBattleship.isVertical)
                        checkRow = row + distanceFromCenter
                    else checkCol = col + distanceFromCenter
                    if(isValidBattleshipPlacement(selectedBattleship, checkRow, checkCol, boardIdArray)) {
                        removeBattleshipFromBoardIdArray(selectedBattleship, boardIdArray)
                        moveBattleshipToPlacement(selectedBattleship, checkRow, checkCol, boardIdArray)
                        battleshipMoved = true
                        break
                    }
                }
            }
            if(!battleshipMoved) attemptToRotateBattleship(row, col, boardIdArray, false)
        }
    }

    private fun selectBoardSquare(row: Int, col: Int) {
        val opponentsBoardMarkers = getOpponentsBoardMarkers(isP1sMove)
        selectedRow = row; selectedCol = col
        opponentsBoardMarkers[opponentsBoardMarkers.lastIndex].moveTo(
            boardSquares[row][col].x, boardSquares[row][col].y
        )
    }

    private fun getBattleships(forPlayer1: Boolean): ArrayList<Battleship> {
        return if (forPlayer1) battleshipsP1 else battleshipsP2
    }

    private fun getDestroyedShips(forPlayer1: Boolean): ArrayList<GSprite> {
        return if (forPlayer1) destroyedShipsP1 else destroyedShipsP2
    }

    private fun getOpponentsBoardMarkers(forPlayer1: Boolean): ArrayList<GSprite> {
        return if (forPlayer1) boardMarkersP1 else boardMarkersP2
    }

    private fun getBoardIdArray(forPlayer1: Boolean): Array<Array<String>> {
        return if (forPlayer1) boardIdArrayP1 else boardIdArrayP2
    }

    private fun getDestroyedShipsBooleans(forPlayer1: Boolean): ArrayList<Boolean> {
        return if (forPlayer1) battleshipsDestroyedP1 else battleshipsDestroyedP2
    }

    private fun getShipScores(forPlayer1: Boolean): ArrayList<GSprite> {
        return if (forPlayer1) rightShipScores else leftShipScores
    }

    private fun getPlayersTurnNumber(forPlayer1: Boolean): String {
        return if (forPlayer1) "1" else "2"
    }

    private fun hideElement(element: GSprite) {
        while (element.x < width) element.moveBy(width.toFloat(), 0f)
    }

    private fun showElement(element: GSprite) {
        if (element.x > width) element.moveBy(-width.toFloat(), 0f)
    }

    private fun logBoard(forPlayer1: Boolean) {
        val playerNumber = if (forPlayer1) 1 else 2
        var boardState = "P$playerNumber\n"
        val boardIdArray = getBoardIdArray(forPlayer1)
        for (row in 0 until battleshipBoardLength) {
            for (col in 0 until battleshipBoardLength) {
                boardState += boardIdArray[row][col]
                boardState += when (boardIdArray[row][col].length) {
                    1 -> "     "; 2 -> "    "; 3 -> "   "; 4 -> "  "; else -> " "
                }
            }
            boardState += "\n"
        }
//        Log.d("dtag", boardState)
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
    private var playerIsSettingUp = true
    private var displayingOpponentsBoard = false
    private var animationInProgress = false
    private var actionInProgress = false
    private var gameIsReady = false
    private var gameIsOver = false
    private var battleshipsDestroyedP1 = ArrayList<Boolean>()
    private var battleshipsDestroyedP2 = ArrayList<Boolean>()

    // Selected Square
    private var selectedRow = -1
    private var selectedCol = -1

    // Battleship Images
    private val battleshipImages = ArrayList<Bitmap>()
    private val selectedShipImages = ArrayList<Bitmap>()
    private val destroyedShipImages = ArrayList<Bitmap>()
    private val battleshipNoScoreImages = ArrayList<Bitmap>()
    private val battleshipScoreImages = ArrayList<Bitmap>()
    private val boardMarkersImages = ArrayList<Bitmap>()
    private val prevMessageButtonImages = ArrayList<Bitmap>()
    private val nextMessageButtonImages = ArrayList<Bitmap>()

    // Battleship Sprites
    private val battleshipsP1 = ArrayList<Battleship>()
    private val battleshipsP2 = ArrayList<Battleship>()
    private val destroyedShipsP1 = ArrayList<GSprite>()
    private val destroyedShipsP2 = ArrayList<GSprite>()
    private val leftShipScores = ArrayList<GSprite>()
    private val rightShipScores = ArrayList<GSprite>()
    private val boards = ArrayList<GSprite>()

    // Element Sizing (lateinit)
    private var boardWidth = .0
    private var elementSpacing = .0
    private var shipScoreSpacing = .0
    private var messageAreaWidth = .0
    private var boardSquareSize = .0
    private var messagesBarWidth = .0
    private var buttonWidth = .0
    private var buttonHeight = .0
    private var fontSize = .0

    // Sizes in 1080 Width
    private val fontSizeIn1080 = 50.0
    private val elementSpacingIn1080 = 20.0
    private val shipScoreSpacingIn1080 = -5.0

    // Clickable Board Squares
    private lateinit var boardSquares: Array<Array<GRect>>

    // Board Markers for Each Board
    private var boardMarkersP1 = ArrayList<GSprite>()
    private var boardMarkersP2 = ArrayList<GSprite>()

    // Board Arrays
    private lateinit var boardIdArrayP1: Array<Array<String>>
    private lateinit var boardIdArrayP2: Array<Array<String>>

    // Battleship Top Elements
    private var messagesBar = GCompound()
    private var menuButton = GCompound()
    private var restartButton = GCompound()
    private var readyButton = GCompound()
    private lateinit var messagesBarLabel: GLabel
    private lateinit var rotateButton: GSprite
    private lateinit var messagesButton: GSprite
    private lateinit var switchBoardButton: GSprite
    private lateinit var prevMessageButton: GSprite
    private lateinit var nextMessageButton: GSprite

    // Currently Selected Battleship
    private var selectedBattleship = Battleship()

    // Screen Distribution Percentages
    private val percentOfWidthTheBoardTakes = .9

    private fun initShipSetup() {
        moveBattleshipsToRandomLocation(battleshipsP1, boardIdArrayP1)
        selectBattleship(0, battleshipsP1)
        gameIsReady = true
    }

    private fun moveBattleshipsToRandomLocation(battleships: ArrayList<Battleship>,
                                                boardIdArray: Array<Array<String>>) {
        for (battleship in battleships) {
            if (Random().nextBoolean()) {
                battleship.sprite.bitmap =
                    battleship.sprite.bitmap.rotate(90f)
                battleship.isVertical = false
            }
            add(battleship.sprite)
            moveElementOffscreen(battleship.sprite)
            var randomRow = Random().nextInt(battleshipBoardLength)
            var randomCol = Random().nextInt(battleshipBoardLength)
            while (!isValidBattleshipPlacement(battleship, randomRow, randomCol, boardIdArray)) {
                randomRow = Random().nextInt(battleshipBoardLength)
                randomCol = Random().nextInt(battleshipBoardLength)
            }
            moveBattleshipToPlacement(battleship, randomRow, randomCol, boardIdArray)
        }
    }

    private fun getStartRowToCheckOrMark(length: Int, row: Int): Int {
        return when (length) {
            5 -> row - 2; 4 -> row - 1; 3 -> row - 1; 2 -> row; else -> row
        }
    }

    private fun getStartColToCheckOrMark(length: Int, col: Int): Int {
        return when (length) {
            5 -> col - 2; 4 -> col - 2; 3 -> col - 1; 2 -> col - 1; else -> col
        }
    }

    private fun isValidBattleshipPlacement(battleship: Battleship, row: Int, col: Int,
                                           boardIdArray: Array<Array<String>>): Boolean {
        if(row < 0 || row >= battleshipBoardLength || col < 0 || col >= battleshipBoardLength)
            return false
        if (battleship.isVertical) {
            val startRowToCheck = getStartRowToCheckOrMark(battleship.length, row)
            for (index in 0 until battleship.length) {
                val rowToCheck = startRowToCheck + index
                if (rowToCheck < 0 || rowToCheck >= battleshipBoardLength) return false
                if (boardIdArray[rowToCheck][col] != oceanID &&
                    !boardIdArray[rowToCheck][col].contains(battleship.id)
                ) return false
            }
        } else {
            val startColToCheck = getStartColToCheckOrMark(battleship.length, col)
            for (index in 0 until battleship.length) {
                val colToCheck = startColToCheck + index
                if (colToCheck < 0 || colToCheck >= battleshipBoardLength) return false
                if (boardIdArray[row][colToCheck] != oceanID &&
                    !boardIdArray[row][colToCheck].contains(battleship.id)
                ) return false
            }
        }
        return true
    }

    private fun moveBattleshipToPlacement(battleship: Battleship, row: Int, col: Int,
                                          boardIdArray: Array<Array<String>>,
                                          displayShip: Boolean = true) {
        if (battleship.isVertical) {
            val startRowToMark = getStartRowToCheckOrMark(battleship.length, row)
            battleship.startRow = startRowToMark
            battleship.startCol = col
            battleship.centerRow = row
            battleship.centerCol = col
            if(displayShip) {
                for (index in 0 until battleship.length)
                    boardIdArray[startRowToMark + index][col] = battleship.id
                boardIdArray[row][col] += verticalID
                battleship.sprite.moveTo(
                    boardSquares[startRowToMark][col].x,
                    boardSquares[startRowToMark][col].y
                )
            }
        } else {
            val startColToMark = getStartColToCheckOrMark(battleship.length, col)
            battleship.startRow = row
            battleship.startCol = startColToMark
            battleship.centerRow = row
            battleship.centerCol = col
            if(displayShip) {
                for (index in 0 until battleship.length)
                    boardIdArray[row][startColToMark + index] = battleship.id
                boardIdArray[row][col] += horizontalID
                battleship.sprite.moveTo(
                    boardSquares[row][startColToMark].x,
                    boardSquares[row][startColToMark].y
                )
            }
        }
    }

    private fun selectBattleship(index: Int, battleships: ArrayList<Battleship>) {
        unselectBattleship()
        battleships[index].sprite.bitmap = selectedShipImages[index]
        if (!battleships[index].isVertical) battleships[index].sprite.bitmap =
            battleships[index].sprite.bitmap.rotate(90f)
        selectedBattleship = battleships[index]
    }

    private fun unselectBattleship() {
        if (selectedBattleship.length != 0) {
            when (selectedBattleship.id) {
                carrierID -> selectedBattleship.sprite.bitmap = battleshipImages[0]
                battleshipID -> selectedBattleship.sprite.bitmap = battleshipImages[1]
                submarineID -> selectedBattleship.sprite.bitmap = battleshipImages[2]
                largeShipID -> selectedBattleship.sprite.bitmap = battleshipImages[3]
                tinyShipID -> selectedBattleship.sprite.bitmap = battleshipImages[4]
            }
            if (!selectedBattleship.isVertical) selectedBattleship.sprite.bitmap =
                selectedBattleship.sprite.bitmap.rotate(90f)
            selectedBattleship = Battleship()
        }
    }

    private fun initSameScreenOptionObjectsSetup() {
        initPlayer2Ships()
        initPlayer2Values()
    }

    private fun initPlayer2Ships() {
        initBattleshipsP2()
        destroyedShipsP2.add(GSprite(destroyedShipImages[0]))
        destroyedShipsP2.add(GSprite(destroyedShipImages[1]))
        destroyedShipsP2.add(GSprite(destroyedShipImages[2]))
        destroyedShipsP2.add(GSprite(destroyedShipImages[3]))
        destroyedShipsP2.add(GSprite(destroyedShipImages[4]))
        for (battleship in destroyedShipsP2) {
            add(battleship)
            moveElementOffscreen(battleship)
        }
    }

    private fun initBattleshipsP2() {
        battleshipsP2.add(getBattleshipObject(battleshipImages[0], 5, carrierID))
        battleshipsP2.add(getBattleshipObject(battleshipImages[1], 4, battleshipID))
        battleshipsP2.add(getBattleshipObject(battleshipImages[2], 3, submarineID))
        battleshipsP2.add(getBattleshipObject(battleshipImages[3], 3, largeShipID))
        battleshipsP2.add(getBattleshipObject(battleshipImages[4], 2, tinyShipID))
    }

    private fun initPlayer2Values() {
        boardMarkersP2.add(GSprite(boardMarkersImages[0]))
        add(boardMarkersP2[0])
        moveElementOffscreen(boardMarkersP2[0])
    }

    private fun buildGame() {
        setSizes()
        setOnscreenElements()
        initValues()
    }

    private fun buildClickableBoard() {
        val boardLocationX = (width - boardWidth) / 2
        val boardLocationY = height - boardWidth - boardLocationX - buttonHeight - elementSpacing

        boardSquareSize = boardWidth / battleshipBoardLength
        val transparent = Paint()
        transparent.setARGB(0, 0, 0, 0)
        boardSquares = Array(battleshipBoardLength) { Array(battleshipBoardLength) { GRect() } }
        for (row in 0 until battleshipBoardLength) {
            for (col in 0 until battleshipBoardLength) {
                val x = (boardLocationX + (boardSquareSize * col)).toFloat()
                val y = (boardLocationY + (boardSquareSize * row)).toFloat()
                val squareLength = boardSquareSize.toFloat()
                boardSquares[row][col].paint = transparent
                boardSquares[row][col].setBounds(x, y, squareLength, squareLength)
                add(boardSquares[row][col])
            }
        }
    }

    private fun setOnscreenElements() {
        setMessagesBar()
        buildClickableBoard()
        setBoardSprites()
        setMenuButton()
        setRestartButton()
        setReadyButton()
        setSwitchBoardButton()
        setBattleshipSprites()
        setShipScoreSprites()
        setMessagesButton()
        setRotateButton()
    }

    private fun setBoardSprites() {
        var boardImage = BitmapFactory.decodeResource(resources, R.drawable.battleshipboard)
        boardImage = Bitmap.createScaledBitmap(boardImage, boardWidth.toInt(),
            boardWidth.toInt(), false)
        boards.add(GSprite(boardImage))
        boards.add(GSprite(boardImage))
        add(boards[0], boardSquares[0][0].x, boardSquares[0][0].y)
        add(boards[1], boards[0].x + width, boards[0].y)
        boards[0].sendToBack()
        boards[1].sendToBack()
    }

    private fun setSizes() {
        fontSize = width / (1080.0 / fontSizeIn1080)
        elementSpacing = width / (1080.0 / elementSpacingIn1080)
        shipScoreSpacing = width / (1080.0 / shipScoreSpacingIn1080)
    }

    private fun setMessagesBar() {
        boardWidth = percentOfWidthTheBoardTakes * width
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
        messagesBar.add(
            messagesBarLabel, (messagesBarSprite.width - messagesBarLabel.width) / 2,
            (buttonHeight - messagesBarLabel.height).toFloat() / 2)
        add(messagesBar, (width - boardWidth).toFloat() / 2,
            (height - buttonHeight - (width - boardWidth) / 2).toFloat())
        setMessagesBarArrows()
    }

    // messagesBarWidth = (A * (D - C)) / (A + B)
    // A = image width; B = image height; C = element spacing; D = board width
    private fun resizeMessagesBar(image: Bitmap): Bitmap {
        val messagesBarWidth = (image.width * (boardWidth - elementSpacing)) /
                (image.width + image.height)
        buttonHeight = image.height * (messagesBarWidth / image.width)
        return Bitmap.createScaledBitmap(
            image,
            messagesBarWidth.toInt(),
            buttonHeight.toInt(),
            false
        )
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

    private fun setReadyButton() {
        val buttonSprite = GSprite(resizeButtonImage(
            BitmapFactory.decodeResource(resources, R.drawable.button_image_green)))
        readyButton.add(buttonSprite)
        val readyButtonLabel = GLabel(resources.getString(R.string.readyText))
        readyButtonLabel.paint = getButtonTextPaint(Color.WHITE)
        readyButtonLabel.fontSize = fontSize.toFloat()
        readyButton.add(readyButtonLabel, (buttonWidth - readyButtonLabel.width).toFloat() / 2,
            (buttonHeight - readyButtonLabel.height).toFloat() / 4)
        val readyButtonAreaWidth = buttonWidth + elementSpacing + buttonHeight
        add(readyButton, (width - readyButtonAreaWidth).toFloat() / 2,
            (boardSquares[0][0].y - buttonHeight.toFloat() - elementSpacing).toFloat())
    }

    private fun setSwitchBoardButton() {
        switchBoardButton = GSprite(resizeButtonImage(
            BitmapFactory.decodeResource(resources, R.drawable.right_arrow_button_image)))
        add(switchBoardButton)
        moveElementOffscreen(switchBoardButton)
    }

    private fun moveElementOffscreen(element: GObject) {
        element.moveTo(width * 2f, height * 2f)
    }

    private fun setBattleshipSprites() {
        val battleshipImageStrip =
            BitmapFactory.decodeResource(resources, R.drawable.battleshipsprites)

        selectedShipImages.add(getBattleshipImage(0, 5, battleshipImageStrip))
        selectedShipImages.add(getBattleshipImage(1, 4, battleshipImageStrip))
        selectedShipImages.add(getBattleshipImage(2, 3, battleshipImageStrip))
        selectedShipImages.add(getBattleshipImage(3, 3, battleshipImageStrip))
        selectedShipImages.add(getBattleshipImage(4, 2, battleshipImageStrip))

        battleshipImages.add(getBattleshipImage(5, 5, battleshipImageStrip))
        battleshipImages.add(getBattleshipImage(6, 4, battleshipImageStrip))
        battleshipImages.add(getBattleshipImage(7, 3, battleshipImageStrip))
        battleshipImages.add(getBattleshipImage(8, 3, battleshipImageStrip))
        battleshipImages.add(getBattleshipImage(9, 2, battleshipImageStrip))

        destroyedShipImages.add(getBattleshipImage(10, 5, battleshipImageStrip))
        destroyedShipImages.add(getBattleshipImage(11, 4, battleshipImageStrip))
        destroyedShipImages.add(getBattleshipImage(12, 3, battleshipImageStrip))
        destroyedShipImages.add(getBattleshipImage(13, 3, battleshipImageStrip))
        destroyedShipImages.add(getBattleshipImage(14, 2, battleshipImageStrip))

        val markersImageStrip =
            BitmapFactory.decodeResource(resources, R.drawable.battleshipmarkers)

        boardMarkersImages.add(getMarkerImage(0, markersImageStrip))
        boardMarkersImages.add(getMarkerImage(1, markersImageStrip))
        boardMarkersImages.add(getMarkerImage(2, markersImageStrip))
        boardMarkersImages.add(getMarkerImage(3, markersImageStrip))
        boardMarkersImages.add(getMarkerImage(4, markersImageStrip))

        battleshipsP1.add(getBattleshipObject(battleshipImages[0], 5, carrierID))
        battleshipsP1.add(getBattleshipObject(battleshipImages[1], 4, battleshipID))
        battleshipsP1.add(getBattleshipObject(battleshipImages[2], 3, submarineID))
        battleshipsP1.add(getBattleshipObject(battleshipImages[3], 3, largeShipID))
        battleshipsP1.add(getBattleshipObject(battleshipImages[4], 2, tinyShipID))

        destroyedShipsP1.add(GSprite(destroyedShipImages[0]))
        destroyedShipsP1.add(GSprite(destroyedShipImages[1]))
        destroyedShipsP1.add(GSprite(destroyedShipImages[2]))
        destroyedShipsP1.add(GSprite(destroyedShipImages[3]))
        destroyedShipsP1.add(GSprite(destroyedShipImages[4]))

        boardMarkersP1.add(GSprite(boardMarkersImages[0]))
        add(boardMarkersP1[0])
        moveElementOffscreen(boardMarkersP1[0])
    }

    private fun getBattleshipImage(index: Int, boardSquareLength: Int,
                                   battleshipImageStrip: Bitmap): Bitmap {
        val nStripColumns = 15.0
        val maxBoardSquareLength = 5.0
        val boardSquareHeight = (battleshipImageStrip.height / maxBoardSquareLength).toInt()
        val stripAreaWidth = (battleshipImageStrip.width / nStripColumns).toInt()
        val stripAreaHeight = boardSquareHeight * boardSquareLength
        val battleshipImage = Bitmap.createBitmap(battleshipImageStrip,
            index * stripAreaWidth, 0, stripAreaWidth, stripAreaHeight
        )
        return scaleImageToFitBoard(battleshipImage, boardSquareLength)
    }

    private fun getMarkerImage(index: Int, markersImageStrip: Bitmap): Bitmap {
        val nStripColumns = 5.0
        val maxBoardSquareLength = 5.0
        val stripAreaWidth = (markersImageStrip.width / nStripColumns).toInt()
        val stripAreaHeight = (markersImageStrip.height / maxBoardSquareLength).toInt()
        val battleshipImage = Bitmap.createBitmap(markersImageStrip,
            index * stripAreaWidth, 0, stripAreaWidth, stripAreaHeight
        )
        return scaleImageToFitBoard(battleshipImage, 1)
    }

    private fun scaleImageToFitBoard(battleshipImage: Bitmap, boardSquareLength: Int): Bitmap {
        return Bitmap.createScaledBitmap(
            battleshipImage, boardSquareSize.toInt(),
            (boardSquareSize * boardSquareLength).toInt(), false
        )
    }

    private fun getBattleshipObject(image: Bitmap, length: Int, id: String): Battleship {
        val battleship = Battleship()
        battleship.sprite.bitmap = image
        battleship.length = length
        battleship.id = id
        return battleship
    }

    private fun setShipScoreSprites() {
        val shipScoreImageStrip =
            BitmapFactory.decodeResource(resources, R.drawable.shipscoresprites)

        battleshipNoScoreImages.add(getShipScoreImage(0, 5, shipScoreImageStrip))
        battleshipNoScoreImages.add(getShipScoreImage(1, 4, shipScoreImageStrip))
        battleshipNoScoreImages.add(getShipScoreImage(3, 3, shipScoreImageStrip))
        battleshipNoScoreImages.add(getShipScoreImage(2, 3, shipScoreImageStrip))
        battleshipNoScoreImages.add(getShipScoreImage(4, 2, shipScoreImageStrip))

        battleshipScoreImages.add(getShipScoreImage(5, 5, shipScoreImageStrip))
        battleshipScoreImages.add(getShipScoreImage(6, 4, shipScoreImageStrip))
        battleshipScoreImages.add(getShipScoreImage(8, 3, shipScoreImageStrip))
        battleshipScoreImages.add(getShipScoreImage(7, 3, shipScoreImageStrip))
        battleshipScoreImages.add(getShipScoreImage(9, 2, shipScoreImageStrip))

        addShipScoresSprites()
    }

    private fun getShipScoreImage(index: Int, boardSquareLength: Int,
                                  battleshipImageStrip: Bitmap): Bitmap {
        val nStripColumns = 10
        val maxBoardSquareLength = 5
        val boardSquareHeight = battleshipImageStrip.height / maxBoardSquareLength
        val stripAreaWidth = battleshipImageStrip.width / nStripColumns
        val stripAreaHeight = boardSquareHeight * boardSquareLength
        return resizeShipScoreImage(
            (Bitmap.createBitmap(
                battleshipImageStrip, index * stripAreaWidth,
                0, stripAreaWidth, stripAreaHeight
            )).rotate(-90f), boardSquareLength
        )
    }

    private fun resizeShipScoreImage(image: Bitmap, boardSquareLength: Int): Bitmap {
        val offsetSide = boardSquares[0][0].x
        val widthForFirstToResizeTo = menuButton.x - offsetSide
        val widthToResizeTo = widthForFirstToResizeTo * (boardSquareLength / 5.0).toFloat()
        val heightToResizeTo = image.height * (widthToResizeTo / image.width)
        return Bitmap.createScaledBitmap(
            image,
            widthToResizeTo.toInt(),
            heightToResizeTo.toInt(),
            false
        )
    }

    private fun addShipScoresSprites() {
        val offsetSide = boardSquares[0][0].x
        val startY = menuButton.y
        for (i in 0 until 5) {
            val spacingMultiplier = when(i) {
                4 -> 2.5
                3 -> 2.7
                2 -> 1.5
                else -> 1.0
            }
            leftShipScores.add(GSprite(battleshipNoScoreImages[i]))
            add(
                leftShipScores[i],
                offsetSide,
                (startY + (i * (leftShipScores[i].height + (shipScoreSpacing * spacingMultiplier)))).toFloat()
            )
            rightShipScores.add(GSprite(flipImage(battleshipNoScoreImages[i])))
            add(
                rightShipScores[i], width - offsetSide - rightShipScores[i].width,
                (startY + (i * (leftShipScores[i].height + (shipScoreSpacing * spacingMultiplier)))).toFloat()
            )
        }
    }

    private fun flipImage(shipScoreImage: Bitmap): Bitmap {
        val matrix = Matrix().apply { postScale(-1f, 1f,
            shipScoreImage.width / 2f, shipScoreImage.height / 2f) }
        return Bitmap.createBitmap(shipScoreImage, 0, 0,
            shipScoreImage.width, shipScoreImage.height, matrix, true)
    }

    private fun resizeIconImage(rotateButtonImage: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(
            rotateButtonImage,
            buttonHeight.toInt(), buttonHeight.toInt(), false
        )
    }

    private fun setMessagesButton() {
        val messagesButtonImage = BitmapFactory.decodeResource(resources, R.drawable.messages_icon)
        messagesButton = GSprite(resizeIconImage(messagesButtonImage))
        add(messagesButton, (messagesBar.x + boardWidth - messagesButton.width).toFloat(), messagesBar.y)
    }

    private fun setRotateButton() {
        val rotateButtonImage = BitmapFactory.decodeResource(resources, R.drawable.rotate_icon)
        rotateButton = GSprite(resizeIconImage(rotateButtonImage))
        add(rotateButton, (readyButton.x + buttonWidth + elementSpacing).toFloat(), readyButton.y)
    }

    private fun initValues() {
        for (i in 0 until 5) battleshipsDestroyedP1.add(false)
        for (i in 0 until 5) battleshipsDestroyedP2.add(false)
        boardIdArrayP1 = Array(battleshipBoardLength) { Array(battleshipBoardLength) { oceanID } }
        boardIdArrayP2 = Array(battleshipBoardLength) { Array(battleshipBoardLength) { oceanID } }
        for (battleship in destroyedShipsP1) {
            add(battleship)
            moveElementOffscreen(battleship)
        }
    }

    class Battleship {
        var sprite = GSprite()
        var length = 0
        var isVertical = true
        var startRow = -1
        var startCol = -1
        var centerRow = -1
        var centerCol = -1
        lateinit var id: String
    }
}