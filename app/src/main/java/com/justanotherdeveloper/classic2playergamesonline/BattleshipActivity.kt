package com.justanotherdeveloper.classic2playergamesonline

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_battleship.*
import kotlin.collections.ArrayList

class BattleshipActivity : AppCompatActivity() {

    // Messaging Values
    private lateinit var onlineMessagesDialog: BottomSheetDialog
    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var gameMessages: ArrayList<View>
    private var listeningForMessages = false
    private var loadedMessages = false

    // Activity Values
    private var gameRoomKey = ""
    private var roomCode = ""
    private var roomIsDeleted = false
    var playerId = 0
    var opponentId = 0
    var activityIsActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battleship)
        battleshipcanvas.linkedActivity = this
        setupActivity()
    }

    private fun setupActivity() {
        onlineMessagesDialog = BottomSheetDialog(this)
        when(intent.getStringExtra(setupModeRef)!!) {
            newSetupRef -> {
                initStartGameProcess(this, battleshipRef)
                battleshipcanvas.playerIsHost = true
            }
            joinSetupRef -> getGameRoomValues(intent.getStringExtra(roomCodeRef)!!,
                true)
            resumeSetupRef -> updateGameWithRoomValues(intent.getStringExtra(roomValuesRef)!!)
        }
    }

    private fun updateGameWithRoomValues(roomValues: String) {
        val roomValuesContents = roomValues.split("\t")
        battleshipcanvas.playerIsHost = roomValuesContents[0].toBoolean()
        playerId = roomValuesContents[1].toInt()
        opponentId = roomValuesContents[2].toInt()
        gameRoomKey = roomValuesContents[3]
        roomCode = roomValuesContents[4]

        val fb = FirebaseDatabase.getInstance().reference
        val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
        val activity = this
        gameRoom.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                if(data.value == null) {
                    if(activityIsActive)
                        displayRoomDeletedDialog(activity)
                    return
                }
                val boardState = data.child(boardStatePath).value.toString()
                val hostMovesFirst = data.child(hostMovesFirstPath).value.toString()
                battleshipcanvas.post {
                    loadRoomMessages()
                    determineGameStateToResumeGame(boardState, hostMovesFirst)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive)
                    displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    private fun determineGameStateToResumeGame(boardState: String, hostMovesFirstString: String) {
        val fb = FirebaseDatabase.getInstance().reference
        val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
        var hostMovesFirst = hostMovesFirstString
        if(hostMovesFirstString.contains("restart")) {
            hostMovesFirst = hostMovesFirstString.split("\t")[1]
            if(hostMovesFirstString.contains(playerId.toString()))
                displayRestartRequestedDialog(this, gameRoomKey, battleshipRef, playerId, battleshipcanvas.hostMovesFirst)
        }
        when(hostMovesFirst) {
            "null" -> {
                if(battleshipcanvas.playerIsHost)
                    promptForFirstMove(this, battleshipRef, gameRoom, roomCode)
                else initJoinGameProcess(this, battleshipRef, gameRoomKey, roomCode)
            }
            else -> {
                if(boardState.contains("!!")) {
                    if(boardState.contains("&&")) {
                        val idOfBoardStatePlayer =
                            boardState.split("&&")[1].split("!!")[0].toInt()
                        if(idOfBoardStatePlayer == playerId) {
                            battleshipcanvas.hostMovesFirst = hostMovesFirst.toBoolean()
                            battleshipcanvas.displayPlayersShipsToResumeStartingGame(boardState.split("&&")[0])
                            battleshipcanvas.updateMessageBarText(getString(R.string.opponentSettingUpOnlinePrompt))
                            listenForOpponentsBoardState(boardState.split("&&")[0])
                        } else battleshipcanvas.setPlayerWithFirstMove(hostMovesFirst.toBoolean())
                    } else battleshipcanvas.setPlayerWithFirstMove(hostMovesFirst.toBoolean())
                } else {
                    battleshipcanvas.hostMovesFirst = hostMovesFirst.toBoolean()
                    battleshipcanvas.setupToResumeGame(boardState)
                }
                listenForRestartGameRequest()
                battleshipcanvas.displayRoomCode(roomCode)
            }
        }
    }

    private fun getGameRoomValues(roomCode: String, initJoinGameProcess: Boolean = false) {
        val fb = FirebaseDatabase.getInstance().reference
        val gameRooms = fb.child(gameRoomsPath)
        val roomCodes = gameRooms.orderByChild(roomCodePath).equalTo(roomCode)
        val activity = this
        this.roomCode = roomCode
        roomCodes.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                val dataIterator = data.children.iterator()
                if(!dataIterator.hasNext()) {
                    if(activityIsActive)
                        displayRoomDeletedDialog(activity)
                    return
                }
                val gameRoom = dataIterator.next()
                gameRoomKey = gameRoom.key!!
                opponentId = if(battleshipcanvas.playerIsHost)
                    gameRoom.child(clientIdPath).value.toString().toInt()
                else gameRoom.child(hostIdPath).value.toString().toInt()
                if(initJoinGameProcess)
                    initJoinGameProcess(activity, battleshipRef, gameRoomKey, roomCode)
                else {
                    listenForIncomingMessages()
                    listenForRestartGameRequest()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive)
                    displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    fun blockScreenToChangePlayer(prompt: String): Button {
        return initChangeTurnScreenBlocker(this, prompt)
    }

    fun openMenu(forOnline: Boolean = true) {
        initMenuSetup(this, forOnline, battleshipRef)
    }

    fun setupForSameScreenOption() {
        battleshipcanvas.setupForSameScreenOption()
    }

    fun setPlayerWithFirstMove(hostMovesFirst: Boolean, roomCode: String) {
        if(gameRoomKey == "") getGameRoomValues(roomCode) else {
            listenForIncomingMessages()
            listenForRestartGameRequest()
        }
        battleshipcanvas.displayRoomCode(roomCode)
        playerId = getSharedPreferences(sharedPrefFilename, MODE_PRIVATE).getInt(playerIdRef, 0)
        battleshipcanvas.setPlayerWithFirstMove(hostMovesFirst)
    }

    fun waitForOpponentsMove() {
        val fb = FirebaseDatabase.getInstance().reference
        val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
        val activity = this
        gameRoom.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                if(data.value == null || !activityIsActive) { gameRoom.removeEventListener(this); return }
                val restartGamePathResult = data.child(hostMovesFirstPath).value.toString()
                if(restartGamePathResult == "null") gameRoom.removeEventListener(this)
                else if(!restartGamePathResult.contains("restart") &&
                    battleshipcanvas.getOpponentsMove(data.child(boardStatePath).value.toString()))
                    gameRoom.removeEventListener(this)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive)
                    displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    private fun loadRoomMessages() {
        val activity = this
        val fb = FirebaseDatabase.getInstance().reference
        val messagesRoom = fb.child("$messagesPath/$roomCode").orderByKey()
        messagesRoom.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                if(data.value == null) {
                    listenForIncomingMessages()
                    return
                }
                loadedMessages = true
                val messagesSize = battleshipcanvas.messages.size
                var messageIndex = messagesSize - 1
                val dataIterator = data.children.iterator()
                while(dataIterator.hasNext()) {
                    val nextData = dataIterator.next()
                    val messageString = nextData.child(messagePath).value.toString()
                    val messageContents = messageString.split("\t")
                    val fromId = messageContents[1].toInt()
                    val message = messageContents[0]
                    if(messagesSize == 0) battleshipcanvas.updateMessageBarText(message, true, fromId)
                    else battleshipcanvas.messages.add(messageIndex++, messageString)
                }
                battleshipcanvas.recalibrateMessageBar()
                listenForIncomingMessages()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive)
                    displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    private fun listenForIncomingMessages() {
        if(listeningForMessages) return
        listeningForMessages = true
        val activity = this
        val fb = FirebaseDatabase.getInstance().reference
        val messagesRoom = fb.child("$messagesPath/$roomCode")
            .orderByKey().limitToLast(1)
        var firstMessageReceived = false
        messagesRoom.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                if(!activityIsActive) messagesRoom.removeEventListener(this)
                if(data.value == null) return
                if(!firstMessageReceived) {
                    firstMessageReceived = true
                    if(loadedMessages) return
                }
                val messageString =
                    data.children.iterator().next().child(messagePath).value.toString()
                val messageContents = messageString.split("\t")
                val fromId = messageContents[1].toInt()
                val message = messageContents[0]
                battleshipcanvas.updateMessageBarText(message, true, fromId)
                showReceivedMessage(message, fromId)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive)
                    displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    @SuppressLint("InflateParams")
    fun showReceivedMessage(message: String, fromId: Int) {
        if(!onlineMessagesDialog.isShowing) return
        val messageTextView: View
        when(fromId) {
            playerId -> {
                messageTextView =
                    layoutInflater.inflate(R.layout.widget_player_message, null)
                messageTextView.findViewById<TextView>(R.id.playerMessageTextView).text = message
            }
            opponentId -> {
                messageTextView =
                    layoutInflater.inflate(R.layout.widget_opponent_message, null)
                messageTextView.findViewById<TextView>(R.id.opponentMessageTextView).text = message
            }
            else -> {
                val hideGameMessages = getSharedPreferences(sharedPrefFilename,
                    MODE_PRIVATE).getBoolean(hideBattleshipMessagesRef, false)
                messageTextView =
                    layoutInflater.inflate(R.layout.widget_game_message, null)
                messageTextView.findViewById<TextView>(R.id.gameMessageTextView).text = message
                if(hideGameMessages) messageTextView.isVisible = false
                gameMessages.add(messageTextView)
            }
        }
        messagesLayout.addView(messageTextView)
        messagesLayout.post {
            if(scrollView.height < messagesLayout.height) {
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT)
                params.gravity = Gravity.TOP
                messagesLayout.layoutParams = params
                scrollView.scrollTo(0, messagesLayout.height)
            }
        }
    }

    fun addSentMessage(message: String) {
        val fb = FirebaseDatabase.getInstance().reference
        val messagesRoom = fb.child("$messagesPath/$roomCode")
        val newMessage = messagesRoom.push()
        newMessage.child(messagePath).setValue(message)
        updateGameDate(this, gameRoomKey)
    }

    fun openMessagesForOnlineOption(messages: ArrayList<String>) {
        val (scrollView, messagesLayout,
            onlineMessagesDialog, gameMessages) =
            initOnlineOptionOpenMessages(this, messages, playerId, battleshipRef)
        this.scrollView = scrollView
        this.messagesLayout = messagesLayout
        this.onlineMessagesDialog = onlineMessagesDialog
        this.gameMessages = gameMessages
    }

    fun openMessagesForSameScreenOption(messages: ArrayList<String>) {
        initSameScreenOptionOpenMessages(this, messages)
    }

    fun notifyFirebaseThatShipsAreSetup(boardState: String) {
        val fb = FirebaseDatabase.getInstance().reference
        val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
        val activity = this
        gameRoom.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                if(data.value == null) return
                val savedBoardState = data.child(boardStatePath).value.toString()
                    .split("!!")[0].split("&&")[0]
                if(savedBoardState == "null") {
                    data.child(boardStatePath).ref.setValue("$boardState&&$playerId!!${getDateString()}")
                    battleshipcanvas.updateMessageBarText(getString(R.string.opponentSettingUpOnlinePrompt))
                    listenForOpponentsBoardState(boardState)
                } else {
                    var newBoardState: String
                    if(battleshipcanvas.playerIsHost) {
                        newBoardState = boardState + "\t" + savedBoardState + "\t" + getDateString()
                        newBoardState += if(battleshipcanvas.hostMovesFirst)
                            "\t$playerId:" else "\t$opponentId:"
                    } else {
                        newBoardState = savedBoardState + "\t" + boardState + "\t" + getDateString()
                        newBoardState += if(!battleshipcanvas.hostMovesFirst)
                            "\t$playerId:" else "\t$opponentId:"
                    }
                    data.child(boardStatePath).ref.setValue(newBoardState)
                    battleshipcanvas.updateOpponentsBoardState(newBoardState)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive)
                    displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    private fun listenForOpponentsBoardState(boardState: String) {
        val fb = FirebaseDatabase.getInstance().reference
        val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
        val activity = this
        gameRoom.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                if(data.value == null || !activityIsActive) { gameRoom.removeEventListener(this); return }
                val savedBoardState = data.child(boardStatePath).value.toString().split("&&")[0]
                if(savedBoardState != boardState) {
                    battleshipcanvas.updateOpponentsBoardState(savedBoardState)
                    gameRoom.removeEventListener(this)
                }
                val restartGamePathResult = data.child(hostMovesFirstPath).value.toString()
                if(restartGamePathResult == "null") gameRoom.removeEventListener(this)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive)
                    displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    private fun listenForRestartGameRequest() {
        val fb = FirebaseDatabase.getInstance().reference
        val restartGamePath = fb.child("$gameRoomsPath/$gameRoomKey/$hostMovesFirstPath")
        val activity = this
        var firstNullChanged = false
        restartGamePath.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                if(data.value == null || !activityIsActive) {
                    if(data.value == null) {
                        if(activityIsActive)
                            displayRoomDeletedDialog(activity)
                        roomIsDeleted = true
                    }
                    restartGamePath.removeEventListener(this)
                    return
                }
                val hostMovesFirstContents = data.value.toString().split("\t")
                if(!firstNullChanged)
                    if(hostMovesFirstContents[0] != "null") firstNullChanged = true
                    else return
                when(hostMovesFirstContents[0]) {
                    "$opponentId requested restart" -> initRestartOnlineGameProcess(
                        activity, battleshipRef, hostMovesFirstContents[1], restartGamePath,
                        fb.child("$gameRoomsPath/$gameRoomKey"), this)
                    "null" -> restartGamePath.removeEventListener(this)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive)
                    displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    fun sameScreenRestartClicked() {
        initConfirmSameScreenRestart(this, battleshipRef)
    }

    fun initRestartSameScreenGame() {
        battleshipcanvas.initRestartSameScreenGame()
    }

    fun initRestartOnlineGame() {
        battleshipcanvas.restartOnlineGame()
    }

    fun displayAppropriateDialogForRestart() {
        if(activityIsActive) {
            if(battleshipcanvas.playerIsHost) {
                val fb = FirebaseDatabase.getInstance().reference
                val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
                promptForFirstMove(this, battleshipRef, gameRoom, roomCode)
            }
            else initJoinGameProcess(this, battleshipRef, gameRoomKey, roomCode)
        }
    }

    fun restartOnlineGameButtonClicked() {
        if(roomIsDeleted) return
        displayRestartRequestedDialog(this, gameRoomKey, battleshipRef, playerId, battleshipcanvas.hostMovesFirst)
    }

    fun updateBoardStateInFirebase(boardState: String) {
        if(roomIsDeleted) return
        val fb = FirebaseDatabase.getInstance().reference
        val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
        gameRoom.child(boardStatePath).setValue(boardState)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityIsActive = false
    }
}