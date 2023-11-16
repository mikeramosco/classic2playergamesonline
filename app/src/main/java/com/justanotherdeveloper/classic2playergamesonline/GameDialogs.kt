package com.justanotherdeveloper.classic2playergamesonline

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.*
import kotlin.collections.ArrayList
import androidx.core.view.size
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_active_games.*

@SuppressLint("InflateParams")
fun initStartGameProcess(activity: AppCompatActivity, selectedGame: String) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_options, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<Button>(R.id.bottomsheetOption1Layout).isVisible = false
    view.findViewById<TextView>(R.id.bottomsheetPrompt).text = activity.getString(R.string.playMethodPrompt)

    val optionOnline = view.findViewById<Button>(R.id.bottomsheetOption2)
    val optionSameScreen = view.findViewById<Button>(R.id.bottomsheetOption3)
    val optionCancel = view.findViewById<Button>(R.id.bottomsheetOption4)
    val onlineProgress = view.findViewById<ProgressBar>(R.id.option2ProgressBar)

    optionOnline.text = activity.getString(R.string.onlineText)
    optionSameScreen.text = activity.getString(R.string.sameScreenText)
    optionCancel.text = activity.getString(R.string.cancelText)

    fun buttonClicked() {
        optionOnline.isEnabled = false
        optionSameScreen.isEnabled = false
    }

    optionOnline.setOnClickListener {
        onlineProgress.isVisible = true
        optionOnline.isVisible = false
        buttonClicked()
        val sharedPreferences = activity.getSharedPreferences(sharedPrefFilename, MODE_PRIVATE)
        val playerId = sharedPreferences.getInt(playerIdRef, 0)
        if(playerId == 0) createPlayerIdToCreateRoom(activity, bottomSheetDialog, selectedGame)
        else createRoom(activity, bottomSheetDialog, selectedGame, playerId)
    }

    optionSameScreen.setOnClickListener {
        buttonClicked()
        setupSameScreenOption(activity, selectedGame)
        bottomSheetDialog.cancel()
    }

    optionCancel.setOnClickListener {
        buttonClicked()
        activity.finish()
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

fun generateId(): Int {
    val startRandomID = 100000
    val endRandomID = 999999
    return Random().nextInt((endRandomID + 1) - startRandomID) + startRandomID
}

private fun createPlayerIdToCreateRoom(activity: AppCompatActivity, firstPromptDialog: BottomSheetDialog,
                                       selectedGame: String) {
    val fb = FirebaseDatabase.getInstance().reference
    val playerIds = fb.child(playerIdsPath)
    val ids = playerIds.orderByChild(idPath)
    ids.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(data: DataSnapshot) {
            var idExists = true
            var playerId = generateId()
            while(idExists) {
                idExists = false
                val dataIterator = data.children.iterator()
                while (dataIterator.hasNext()) {
                    val nextData = dataIterator.next()
                    val id = nextData.child(idPath).value.toString().toInt()
                    if (playerId == id) {
                        idExists = true
                        break
                    }
                }
                if(idExists) playerId = generateId()
            }

            val sharedPreferences
                    = activity.getSharedPreferences(sharedPrefFilename, MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putInt(playerIdRef, playerId)
            editor.apply()
            val newGameRoom = playerIds.push()
            newGameRoom.child(idPath).setValue(playerId)
            createRoom(activity, firstPromptDialog, selectedGame, playerId)
        }

        override fun onCancelled(databaseError: DatabaseError) {
            displayMessageDialog(activity, activity.getString(R.string.unableToConnectPrompt))
        }
    })
}

private fun generateRoomCode(): String {
    var roomCode = ""
    for(i in 0 until 4)
        roomCode += getRandomCharacter()
    return roomCode
}

private fun getRandomCharacter(): String {
    val possibleCharacters = "123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    val randomIndex = Random().nextInt(possibleCharacters.length)
    return possibleCharacters[randomIndex].toString()
}

fun getNBoardStateIds(game: String): Int {
    return when(game) {
        battleshipRef -> battleshipBoardLength * battleshipBoardLength * 2
        connect4Ref -> connect4BoardRows * connect4BoardCols
        mastermindRef -> (mastermindLeftBoardRows + mastermindRightBoardRows) *
                mastermindBoardCols * 2 + mastermindBoardCols
        else -> sentinel
    }
}

fun getNDaysSinceLastMove(lastMoveDateString: String, fromDateString: String = getDateString()): Int {
    val lastMoveDateContents = lastMoveDateString.split(":")
    val currentDateContents = fromDateString.split(":")

    val lastMoveMonth = lastMoveDateContents[0].toInt()
    val lastMoveDay = lastMoveDateContents[1].toInt()
    val lastMoveYear = lastMoveDateContents[2].toInt()

    val currentMonth = currentDateContents[0].toInt()
    val currentDay = currentDateContents[1].toInt()
    val currentYear = currentDateContents[2].toInt()

    return daysBetweenDates(lastMoveMonth, lastMoveDay, lastMoveYear,
        currentMonth, currentDay, currentYear)
}

fun Calendar.comesAfter(date: Calendar): Boolean {
    return timeInMillis >= date.timeInMillis
}

fun getTodaysDate(): Calendar {
    return Calendar.getInstance(TimeZone.getDefault()).resetTimeOfDay()
}

fun String.dateStringToCalendar(): Calendar {
    if(!contains(":")) return getTodaysDate()
    val dateContents = split(":")
    if(dateContents.size != 3) return getTodaysDate()
    val month = dateContents[0].toInt() - 1
    val day = dateContents[1].toInt()
    val year = dateContents[2].toInt()
    return createCalendar(year, month, day)
}

fun createCalendar(year: Int, month: Int, day: Int): Calendar {
    val calendar = Calendar.getInstance(TimeZone.getDefault())
    calendar.set(Calendar.YEAR, year)
    calendar.set(Calendar.MONTH, month)
    calendar.set(Calendar.DAY_OF_MONTH, day)
    return calendar.resetTimeOfDay()
}

private fun Calendar.resetTimeOfDay(): Calendar {
    set(Calendar.MILLISECOND, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.HOUR_OF_DAY, 0)
    return this
}

private fun gameOverInactivityThreshold(dateString: String): Boolean {
    val effectiveDate = inactivityDeletionEffectiveDate

    val effectiveDateAsCal = effectiveDate.dateStringToCalendar()
    val dateAsCal = dateString.dateStringToCalendar()

    val dateToUse = if(effectiveDateAsCal.comesAfter(dateAsCal))
        effectiveDate else dateString

    val nDaysSinceLastMove = getNDaysSinceLastMove(dateToUse)

    return nDaysSinceLastMove > inactiveDaysThreshold
}

private fun deleteInactiveGames(data: DataSnapshot) {
    val dataIterator = data.children.iterator()
    val gameRoomKeysToDelete = ArrayList<String>()
    val roomCodesToDelete = ArrayList<String>()

    // find all games to delete
    while (dataIterator.hasNext()) {
        val nextData = dataIterator.next()
        val gameRoomKey = nextData.key!!
        val game = nextData.child(gamePath).value.toString()
        val roomCode = nextData.child(roomCodePath).value.toString()
        val boardState = nextData.child(boardStatePath).value.toString()
        val dateString: String

        if(game != "null" && boardState != "null") {
            dateString = if(boardState.contains("!!"))
                boardState.split("!!")[1]
            else {
                val boardStateContents = boardState.split("\t")
                boardStateContents[getNBoardStateIds(game)]
            }

            // if date is over inactivity threshold (60 days) add to delete list
            if(gameOverInactivityThreshold(dateString)) {
                gameRoomKeysToDelete.add(gameRoomKey)
                roomCodesToDelete.add(roomCode)
            }
        }
    }

    // delete inactive games
    val fb = FirebaseDatabase.getInstance().reference
    val gameRoomsTable = fb.child(gameRoomsPath)
    val messagesTable = fb.child(messagesPath)

    val deletedGameRoomsMap = HashMap<String, Any?>()
    val deletedMessagesMap = HashMap<String, Any?>()

    if(gameRoomKeysToDelete.isNotEmpty()) {
        for (gameRoomKey in gameRoomKeysToDelete)
            deletedGameRoomsMap[gameRoomKey] = null
        gameRoomsTable.updateChildren(deletedGameRoomsMap)
    }

    if(roomCodesToDelete.isNotEmpty()) {
        for (roomCode in roomCodesToDelete)
            deletedMessagesMap[roomCode] = null
        messagesTable.updateChildren(deletedMessagesMap)
    }
}

private fun createRoom(activity: AppCompatActivity, firstPromptDialog: BottomSheetDialog,
                       selectedGame: String, playerId: Int) {
    val fb = FirebaseDatabase.getInstance().reference
    val gameRooms = fb.child(gameRoomsPath)
    val roomCodes = gameRooms.orderByChild(roomCodePath)
    roomCodes.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(data: DataSnapshot) {
            deleteInactiveGames(data)
            var roomCodeExists = true
            var roomCode = generateRoomCode()
            while(roomCodeExists) {
                roomCodeExists = false
                val dataIterator = data.children.iterator()
                while (dataIterator.hasNext()) {
                    val nextData = dataIterator.next()
                    val roomsCode = nextData.child(roomCodePath).value.toString()
                    if (roomsCode == roomCode) {
                        roomCodeExists = true
                        break
                    }
                }
                if(roomCodeExists) roomCode = generateRoomCode()
            }

            val gameRoom = gameRooms.push()
            val newGameRoomMap = HashMap<String, Any>()
            newGameRoomMap[roomCodePath] = roomCode
            newGameRoomMap[hostIdPath] = playerId
            newGameRoomMap[clientIdPath] = 0
            newGameRoomMap[gamePath] = selectedGame
            newGameRoomMap[hostMovesFirstPath] = "null"
            newGameRoomMap[boardStatePath] = "null!!${getDateString()}"
            gameRoom.updateChildren(newGameRoomMap)

//            // OLD CODE
//            gameRoom.child(roomCodePath).setValue(roomCode)
//            gameRoom.child(hostIdPath).setValue(playerId)
//            gameRoom.child(clientIdPath).setValue(0)
//            gameRoom.child(gamePath).setValue(selectedGame)
//            gameRoom.child(hostMovesFirstPath).setValue("null")
//            gameRoom.child(boardStatePath).setValue("null!!${getDateString()}")

            displayRoomCode(activity, roomCode, selectedGame, gameRoom)
            firstPromptDialog.cancel()
        }

        override fun onCancelled(databaseError: DatabaseError) {
            displayMessageDialog(activity, activity.getString(R.string.unableToConnectPrompt))
        }
    })
}

@SuppressLint("InflateParams")
private fun displayRoomCode(activity: AppCompatActivity, roomCode: String, selectedGame: String, gameRoom: DatabaseReference) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_roomcode, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<TextView>(R.id.bottomsheetRoomCode).text = roomCode

    val needHelpButton = view.findViewById<Button>(R.id.bottomsheetRoomCodeHelp)
    val cancelButton = view.findViewById<Button>(R.id.bottomsheetRoomCodeCancel)
    val needHelpDialog = initNeedHelpDialog(activity, selectedGame, needHelpButton, cancelButton)

    fun buttonClicked() {
        needHelpButton.isEnabled = false
        cancelButton.isEnabled = false
    }

    needHelpButton.setOnClickListener {
        buttonClicked()
        try {
            needHelpDialog.show()
        } catch (e: WindowManager.BadTokenException) {
            e.printStackTrace()
        }
    }

    attemptDialogShow(bottomSheetDialog)

    val listener = gameRoom.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(data: DataSnapshot) {
            if(data.value == null) {
                displayRoomDeletedDialog(activity)
                gameRoom.removeEventListener(this)
                if(needHelpDialog.isShowing) needHelpDialog.cancel()
                return
            }
            if(data.child(clientIdPath).value.toString().toInt() != 0) {
                promptForFirstMove(activity, selectedGame, gameRoom, roomCode)
                if(needHelpDialog.isShowing) needHelpDialog.cancel()
                bottomSheetDialog.cancel()
                gameRoom.removeEventListener(this)
            }
        }

        override fun onCancelled(databaseError: DatabaseError) {
            displayMessageDialog(activity, activity.getString(R.string.unableToConnectPrompt))
        }
    })

    cancelButton.setOnClickListener {
        buttonClicked()
        displayConfirmCancel(activity, bottomSheetDialog, gameRoom, listener, needHelpButton, cancelButton)
    }
}

@SuppressLint("InflateParams")
private fun displayConfirmCancel(activity: AppCompatActivity, roomCodeBottomSheetDialog: BottomSheetDialog,
                                 gameRoom: DatabaseReference, listener: ValueEventListener,
                                 needHelpButton: Button, cancelButton: Button) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_confirm_cancel, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    val yesButton = view.findViewById<Button>(R.id.bottomsheetConfirmCancelYes)
    val noButton = view.findViewById<Button>(R.id.bottomsheetConfirmCancelNo)

    fun buttonClicked() {
        yesButton.isEnabled = false
        noButton.isEnabled = false
    }

    yesButton.setOnClickListener {
        buttonClicked()
        gameRoom.removeEventListener(listener)
        gameRoom.setValue(null)
        activity.finish()
        roomCodeBottomSheetDialog.cancel()
        bottomSheetDialog.cancel()
    }

    noButton.setOnClickListener {
        buttonClicked()
        needHelpButton.isEnabled = true
        cancelButton.isEnabled = true
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

@SuppressLint("InflateParams")
fun promptForFirstMove(activity: AppCompatActivity, selectedGame: String, gameRoom: DatabaseReference, roomCode: String) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_options, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    val firstMovePrompt = when(selectedGame) {
        mastermindRef -> activity.getString(R.string.mastermindFirstMovePrompt)
        else -> activity.getString(R.string.firstMovePrompt)
    }

    view.findViewById<Button>(R.id.bottomsheetOption1Layout).isVisible = false
    view.findViewById<TextView>(R.id.bottomsheetPrompt).text = firstMovePrompt

    val optionRandom = view.findViewById<Button>(R.id.bottomsheetOption2)
    val optionOpponent = view.findViewById<Button>(R.id.bottomsheetOption3)
    val optionMyself = view.findViewById<Button>(R.id.bottomsheetOption4)

    optionRandom.text = activity.getString(R.string.pickRandomText)
    optionOpponent.text = activity.getString(R.string.themText)
    optionMyself.text = activity.getString(R.string.youText)

    fun buttonClicked() {
        optionRandom.isEnabled = false
        optionOpponent.isEnabled = false
        optionMyself.isEnabled = false
    }

    optionRandom.setOnClickListener {
        buttonClicked()
        val hostMovesFirst = Random().nextBoolean()
        setValueIfRoomExists(activity, gameRoom, hostMovesFirst.toString())
        setPlayerWithFirstMove(hostMovesFirst, activity, selectedGame, roomCode)
        bottomSheetDialog.cancel()
    }

    optionOpponent.setOnClickListener {
        buttonClicked()
        setValueIfRoomExists(activity, gameRoom, "false")
        setPlayerWithFirstMove(false, activity, selectedGame, roomCode)
        bottomSheetDialog.cancel()
    }

    optionMyself.setOnClickListener {
        buttonClicked()
        setValueIfRoomExists(activity, gameRoom, "true")
        setPlayerWithFirstMove(true, activity, selectedGame, roomCode)
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

private fun setValueIfRoomExists(activity: AppCompatActivity, gameRoom: DatabaseReference,
                                 value: String) {
    gameRoom.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(data: DataSnapshot) {
            if (data.value != null) gameRoom.child(hostMovesFirstPath).setValue(value)
        }

        override fun onCancelled(databaseError: DatabaseError) {
            displayMessageDialog(activity, activity.getString(R.string.unableToConnectPrompt))
        }
    })
}

@SuppressLint("InflateParams")
fun initJoinGameProcess(activity: AppCompatActivity, selectedGame: String, gameRoomKey: String, roomCode: String) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_options, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<Button>(R.id.bottomsheetOption1Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption2Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption3Layout).isVisible = false
    view.findViewById<TextView>(R.id.bottomsheetPrompt).text = activity.getString(R.string.gamePreparingPrompt)

    val optionReturnHome = view.findViewById<Button>(R.id.bottomsheetOption4)

    optionReturnHome.text = activity.getString(R.string.returnHomeText)

    optionReturnHome.setOnClickListener {
        activity.finish()
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)

    val fb = FirebaseDatabase.getInstance().reference
    val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
    gameRoom.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(data: DataSnapshot) {
            if(data.value == null) {
                displayRoomDeletedDialog(activity)
                gameRoom.removeEventListener(this)
                return
            }
            val hostMovesFirst = data.child(hostMovesFirstPath).value.toString()
            if(hostMovesFirst != "null") {
                setPlayerWithFirstMove(hostMovesFirst.toBoolean(), activity, selectedGame, roomCode)
                bottomSheetDialog.cancel()
                gameRoom.removeEventListener(this)
            }
        }

        override fun onCancelled(databaseError: DatabaseError) {
            displayMessageDialog(activity, activity.getString(R.string.unableToConnectPrompt))
        }
    })
}

@SuppressLint("InflateParams")
fun initMenuSetup(activity: AppCompatActivity, forOnline: Boolean, selectedGame: String) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_options, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<TextView>(R.id.bottomsheetPrompt).text = activity.getString(R.string.menuText)

    view.findViewById<Button>(R.id.bottomsheetOption1Layout).isVisible = false
    val optionNeedHelp = view.findViewById<Button>(R.id.bottomsheetOption2)
    val optionActiveGames = view.findViewById<Button>(R.id.bottomsheetOption3)
    val optionReturnHome = view.findViewById<Button>(R.id.bottomsheetOption4)
    val exitButton = view.findViewById<LinearLayout>(R.id.bottomsheetOptionsExitButton)
    val needHelpDialog = initNeedHelpDialog(activity, selectedGame)

    optionNeedHelp.text = activity.getString(R.string.needHelpText)
    optionActiveGames.text = activity.getString(R.string.activeRoomsText)
    optionReturnHome.text = activity.getString(R.string.returnHomeText)
    exitButton.isVisible = true

    fun buttonClicked() {
        optionNeedHelp.isEnabled = false
        optionActiveGames.isEnabled = false
        optionReturnHome.isEnabled = false
    }

    optionNeedHelp.setOnClickListener {
        buttonClicked()
        bottomSheetDialog.cancel()
        try {
            needHelpDialog.show()
        } catch (e: WindowManager.BadTokenException) {
            e.printStackTrace()
        }
    }

    optionActiveGames.setOnClickListener {
        if(forOnline) {
            val data = Intent()
            data.putExtra(openNextRef, "Active Games")
            activity.setResult(RESULT_OK, data)
            activity.finish()
        } else initConfirmQuitSameScreenGame(activity, true)
        buttonClicked()
        bottomSheetDialog.cancel()
    }

    optionReturnHome.setOnClickListener {
        if(forOnline) activity.finish()
        else initConfirmQuitSameScreenGame(activity)
        buttonClicked()
        bottomSheetDialog.cancel()
    }

    exitButton.setOnClickListener {
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

@SuppressLint("InflateParams")
fun initConfirmQuitSameScreenGame(activity: AppCompatActivity, activeGamesClicked: Boolean = false) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_confirm_cancel, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<TextView>(R.id.bottomsheetConfirmCancel).text =
        activity.getString(R.string.confirmQuitSameScreenGamePrompt)
    val yesButton = view.findViewById<Button>(R.id.bottomsheetConfirmCancelYes)
    val noButton = view.findViewById<Button>(R.id.bottomsheetConfirmCancelNo)

    fun buttonClicked() {
        yesButton.isEnabled = false
        noButton.isEnabled = false
    }

    yesButton.setOnClickListener {
        if(activeGamesClicked) {
            val data = Intent()
            data.putExtra(openNextRef, "Active Games")
            activity.setResult(RESULT_OK, data)
        }
        activity.finish()
        buttonClicked()
        bottomSheetDialog.cancel()
    }

    noButton.setOnClickListener {
        buttonClicked()
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

@SuppressLint("InflateParams")
fun initChangeTurnScreenBlocker(activity: AppCompatActivity, prompt: String): Button {
    val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    val view = activity.layoutInflater.inflate(R.layout.dialog_screen_blocker, null)
    dialog.setContentView(view)

    val readyButton = view.findViewById<Button>(R.id.bottomsheetBlockerReadyButton)
    readyButton.isEnabled = false

    view.findViewById<TextView>(R.id.bottomsheetBlockerPrompt).text = prompt
    readyButton.setOnClickListener {
        dialog.cancel()
    }

    try {
        dialog.show()
    } catch (e: WindowManager.BadTokenException) {
        e.printStackTrace()
    }
    return readyButton
}

fun initNeedHelpDialog(activity: AppCompatActivity, selectedGame: String,
                       needHelpButton: Button? = null, cancelButton: Button? = null): Dialog {
    val dialogLayout = when(selectedGame) {
        battleshipRef -> R.layout.dialog_need_help_battleship
        connect4Ref -> R.layout.dialog_need_help_connect4
        mastermindRef -> R.layout.dialog_need_help_mastermind
        else -> R.layout.dialog_need_help_battleship
    }
    val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    val view = activity.layoutInflater.inflate(dialogLayout, null)
    dialog.setContentView(view)

//    val howToPlayShowHideButtonId = when(selectedGame) {
//        battleshipRef -> R.id.howToPlayBattleshipShowHideButton
//        connect4Ref -> R.id.howToPlayConnect4ShowHideButton
//        mastermindRef -> R.id.howToPlayMastermindShowHideButton
//        else -> R.id.howToPlayBattleshipShowHideButton
//    }
//
//    val howToPlayInstructionsId = when(selectedGame) {
//        battleshipRef -> R.id.howToPlayBattleshipInstructions
//        connect4Ref -> R.id.howToPlayConnect4Instructions
//        mastermindRef -> R.id.howToPlayMastermindInstructions
//        else -> R.id.howToPlayBattleshipInstructions
//    }
//
//    val playingOnlineShowHideButtonId = when(selectedGame) {
//        battleshipRef -> R.id.playingOnlineBattleshipShowHideButton
//        connect4Ref -> R.id.playingOnlineConnect4ShowHideButton
//        mastermindRef -> R.id.playingOnlineMastermindShowHideButton
//        else -> R.id.playingOnlineBattleshipShowHideButton
//    }
//
//    val playingOnlineInstructionsId = when(selectedGame) {
//        battleshipRef -> R.id.playingOnlineBattleshipInstructions
//        connect4Ref -> R.id.playingOnlineConnect4Instructions
//        mastermindRef -> R.id.playingOnlineMastermindInstructions
//        else -> R.id.playingOnlineBattleshipInstructions
//    }
//
//    val howToPlayShowHideButton = view.findViewById<TextView>(howToPlayShowHideButtonId)
//    val howToPlayInstructions = view.findViewById<GridLayout>(howToPlayInstructionsId)
//    val playingOnlineShowHideButton = view.findViewById<TextView>(playingOnlineShowHideButtonId)
//    val playingOnlineInstructions = view.findViewById<GridLayout>(playingOnlineInstructionsId)
//
//    howToPlayShowHideButton.setOnClickListener {
//        if(howToPlayShowHideButton.text.toString() == activity.getString(R.string.showText)) {
//            howToPlayInstructions.isVisible = true
//            howToPlayShowHideButton.text = activity.getString(R.string.hideText)
//        } else {
//            howToPlayInstructions.isVisible = false
//            howToPlayShowHideButton.text = activity.getString(R.string.showText)
//        }
//
//    }
//
//    playingOnlineShowHideButton.setOnClickListener {
//        if(playingOnlineShowHideButton.text.toString() == activity.getString(R.string.showText)) {
//            playingOnlineInstructions.isVisible = true
//            playingOnlineShowHideButton.text = activity.getString(R.string.hideText)
//        } else {
//            playingOnlineInstructions.isVisible = false
//            playingOnlineShowHideButton.text = activity.getString(R.string.showText)
//        }
//    }

    val exitButtonId = when(selectedGame) {
        battleshipRef -> R.id.needHelpBattleshipExitButton
        connect4Ref -> R.id.needHelpConnect4ExitButton
        mastermindRef -> R.id.needHelpMastermindExitButton
        else -> R.id.needHelpBattleshipExitButton
    }

    val exitButton = view.findViewById<LinearLayout>(exitButtonId)
    exitButton.setOnClickListener {
        if(needHelpButton != null) {
            needHelpButton.isEnabled = true
            cancelButton!!.isEnabled = true
        }
        dialog.cancel()
    }

    return dialog
}

@SuppressLint("InflateParams")
fun initRestartOnlineGameProcess(activity: AppCompatActivity, selectedGame: String,
                                 hostMovesFirst: String, restartGamePath: DatabaseReference,
                                 gameRoom: DatabaseReference, listener: ValueEventListener? = null) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_options, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<TextView>(R.id.bottomsheetPrompt).text = activity.getString(R.string.opponentRequestedRestartPrompt)

    view.findViewById<Button>(R.id.bottomsheetOption1Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption2Layout).isVisible = false
    val optionYes = view.findViewById<Button>(R.id.bottomsheetOption3)
    val optionNo = view.findViewById<Button>(R.id.bottomsheetOption4)

    optionYes.text = activity.getString(R.string.yesText)
    optionNo.text = activity.getString(R.string.noText)

    fun buttonClicked() {
        optionYes.isEnabled = false
        optionNo.isEnabled = false
    }

    optionYes.setOnClickListener {
        buttonClicked()
        gameRoom.child(boardStatePath).setValue("null!!${getDateString()}")
        if(listener != null) {
            restartGamePath.setValue("null")
            restartGamePath.removeEventListener(listener)
        }
        initRestartOnlineGame(activity, selectedGame)
        bottomSheetDialog.cancel()
    }

    optionNo.setOnClickListener {
        buttonClicked()
        restartGamePath.setValue(hostMovesFirst)
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

@SuppressLint("InflateParams")
fun displayRestartRequestedDialog(activity: AppCompatActivity, gameRoomKey: String,
                                  selectedGame: String, playerId: Int, hostMovesFirst: Boolean) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_options, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<Button>(R.id.bottomsheetOption1Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption2Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption3Layout).isVisible = false
    view.findViewById<TextView>(R.id.bottomsheetPrompt).text = activity.getString(R.string.restartRequestSentPrompt)

    val optionOkay = view.findViewById<Button>(R.id.bottomsheetOption4)
    optionOkay.text = activity.getString(R.string.okayText)

    optionOkay.setOnClickListener {
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)

    val fb = FirebaseDatabase.getInstance().reference
    val restartGamePath = fb.child("$gameRoomsPath/$gameRoomKey/$hostMovesFirstPath")
    restartGamePath.setValue("$playerId requested restart\t$hostMovesFirst")
    restartGamePath.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(data: DataSnapshot) {
            if(data.value == null) {
                restartGamePath.removeEventListener(this)
                return
            }
            val response = data.value.toString()
            if(response == "null") {
                initRestartOnlineGame(activity, selectedGame)
                restartGamePath.removeEventListener(this)
                bottomSheetDialog.cancel()
            } else if(response == "false" || response == "true") {
                displayMessageDialog(activity, activity.getString(R.string.restartRequestDeniedPrompt))
                bottomSheetDialog.cancel()
                restartGamePath.removeEventListener(this)
            }
        }

        override fun onCancelled(databaseError: DatabaseError) {
            displayMessageDialog(activity, activity.getString(R.string.unableToConnectPrompt))
        }
    })
}

@SuppressLint("InflateParams")
fun displayMessageDialog(activity: AppCompatActivity, message: String) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_options, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<Button>(R.id.bottomsheetOption1Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption2Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption3Layout).isVisible = false
    view.findViewById<TextView>(R.id.bottomsheetPrompt).text = message

    val optionOkay = view.findViewById<Button>(R.id.bottomsheetOption4)
    optionOkay.text = activity.getString(R.string.okayText)

    optionOkay.setOnClickListener {
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

@SuppressLint("InflateParams")
fun displayRoomDeletedDialog(activity: AppCompatActivity) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_options, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<Button>(R.id.bottomsheetOption1Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption2Layout).isVisible = false
    view.findViewById<Button>(R.id.bottomsheetOption3Layout).isVisible = false
    view.findViewById<TextView>(R.id.bottomsheetPrompt).text = activity.getString(R.string.roomWasDeletedPrompt)

    val optionOkay = view.findViewById<Button>(R.id.bottomsheetOption4)
    optionOkay.text = activity.getString(R.string.okayText)

    optionOkay.setOnClickListener {
        activity.finish()
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

@SuppressLint("InflateParams")
fun initSameScreenOptionOpenMessages(activity: AppCompatActivity, messages: ArrayList<String>) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_same_screen_messages, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    // Loads the 10 most recent messages
    val messagesLayout = view.findViewById<LinearLayout>(R.id.sameScreenMessagesView)
    var indexOfNextMessageToLoad = messages.lastIndex
    var allMessagesLoaded = false
    var loadingMessages = false
    for(index in messages.lastIndex downTo messages.lastIndex - nMessagesPerInterval + 1) {
        if(index < 0) break
        indexOfNextMessageToLoad--
        val messageTextView = activity.layoutInflater.inflate(R.layout.widget_game_message, null)
        val message = messages[index].split("\t")[0]
        messageTextView.findViewById<TextView>(R.id.gameMessageTextView).text = message
        messagesLayout.addView(messageTextView, 0)
    }
    if(indexOfNextMessageToLoad < 0) allMessagesLoaded = true
    if(!allMessagesLoaded)
        messagesLayout.addView(activity.layoutInflater.inflate(R.layout.widget_loading_messages, null), 0)

    // when player scrolls to top of messages, the 10 next most recent will load
    val scrollView = view.findViewById<ScrollView>(R.id.sameScreenMessagesScrollView)
    messagesLayout.post { scrollView.scrollTo(0, messagesLayout.height) }
    scrollView.viewTreeObserver.addOnScrollChangedListener {
        if (scrollView.getChildAt(0).top == scrollView.scrollY && !allMessagesLoaded && !loadingMessages) {
            loadingMessages = true
            val startingHeight = messagesLayout.height

            // first adds the messages to the bottom of the layout to measure the height
            for(index in indexOfNextMessageToLoad downTo indexOfNextMessageToLoad - nMessagesPerInterval + 1) {
                if(index < 0) break
                val messageTextView = activity.layoutInflater.inflate(R.layout.widget_game_message, null)
                val message = messages[index].split("\t")[0]
                messageTextView.findViewById<TextView>(R.id.gameMessageTextView).text = message
                messagesLayout.addView(messageTextView)
            }

            // once the layout has updated its height, the messages at the bottom
            // of the layout are removed and they are added at the top
            messagesLayout.post {
                messagesLayout.removeViewAt(0)
                for(index in indexOfNextMessageToLoad downTo indexOfNextMessageToLoad - nMessagesPerInterval + 1) {
                    if(index < 0) break
                    indexOfNextMessageToLoad--
                    val messageTextView = activity.layoutInflater.inflate(R.layout.widget_game_message, null)
                    val message = messages[index].split("\t")[0]
                    messageTextView.findViewById<TextView>(R.id.gameMessageTextView).text = message
                    messagesLayout.addView(messageTextView, 0)
                    messagesLayout.removeViewAt(messagesLayout.size-1)
                }
                scrollView.scrollTo(0, messagesLayout.height - startingHeight)
                if(indexOfNextMessageToLoad < 0) allMessagesLoaded = true
                if(!allMessagesLoaded)
                    messagesLayout.addView(activity.layoutInflater.inflate(R.layout.widget_loading_messages, null), 0)
                loadingMessages = false
            }
        }
    }

    val exitButton = view.findViewById<LinearLayout>(R.id.bottomsheetSameScreenMessagesExitButton)
    exitButton.setOnClickListener { bottomSheetDialog.cancel() }
    attemptDialogShow(bottomSheetDialog)
}

@SuppressLint("InflateParams")
fun initOnlineOptionOpenMessages(activity: AppCompatActivity, messages: ArrayList<String>,
                                 playerId: Int, selectedGame: String): Result {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_online_messages, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    val hideGameMessagesReference = when(selectedGame) {
        battleshipRef -> hideBattleshipMessagesRef
        connect4Ref -> hideConnect4MessagesRef
        mastermindRef -> hideMastermindMessagesRef
        else -> hideBattleshipMessagesRef
    }
    val gameMessages = ArrayList<View>()
    var hideGameMessages = activity.getSharedPreferences(sharedPrefFilename,
        AppCompatActivity.MODE_PRIVATE).getBoolean(hideGameMessagesReference, false)

    // Loads the 10 most recent messages
    val messagesLayout = view.findViewById<LinearLayout>(R.id.onlineMessagesView)
    var indexOfNextMessageToLoad = messages.lastIndex
    var allMessagesLoaded = false
    var loadingMessages = false
    var index = messages.lastIndex
    var count = 0
    while(count < nMessagesPerInterval) {
        if(index < 0) break
        indexOfNextMessageToLoad--
        val messageContent = messages[index--].split("\t")
        val message = messageContent[0]
        val fromId = messageContent[1].toInt()
        val layout = when(fromId) {
            gameMessageId -> R.layout.widget_game_message
            playerId -> R.layout.widget_player_message
            else -> R.layout.widget_opponent_message
        }
        val messageTextView = activity.layoutInflater.inflate(layout, null)
        val messageText: TextView = when(fromId) {
            gameMessageId -> messageTextView.findViewById(R.id.gameMessageTextView)
            playerId -> messageTextView.findViewById(R.id.playerMessageTextView)
            else -> messageTextView.findViewById(R.id.opponentMessageTextView)
        }
        messageText.text = message
        if(fromId == gameMessageId) gameMessages.add(messageTextView)
        if(hideGameMessages && fromId == gameMessageId) messageTextView.isVisible = false
        else count++
        messagesLayout.addView(messageTextView, 0)
    }
    if(indexOfNextMessageToLoad < 0) allMessagesLoaded = true
    if(!allMessagesLoaded)
        messagesLayout.addView(activity.layoutInflater.inflate(R.layout.widget_loading_messages, null), 0)

    // when player scrolls to top of messages, the 10 next most recent will load
    val scrollView = view.findViewById<ScrollView>(R.id.onlineMessagesScrollView)
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

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (scrollView.getChildAt(0).top == scrollView.scrollY && !allMessagesLoaded && !loadingMessages) {
            loadingMessages = true
            val startingHeight = messagesLayout.height

            // first adds the messages to the bottom of the layout to measure the height
            index = indexOfNextMessageToLoad
            count = 0
            while(count < nMessagesPerInterval) {
                if(index < 0) break
                val messageContent = messages[index--].split("\t")
                val message = messageContent[0]
                val fromId = messageContent[1].toInt()
                val layout = when(fromId) {
                    gameMessageId -> R.layout.widget_game_message
                    playerId -> R.layout.widget_player_message
                    else -> R.layout.widget_opponent_message
                }
                val messageTextView = activity.layoutInflater.inflate(layout, null)
                val messageText: TextView = when(fromId) {
                    gameMessageId -> messageTextView.findViewById(R.id.gameMessageTextView)
                    playerId -> messageTextView.findViewById(R.id.playerMessageTextView)
                    else -> messageTextView.findViewById(R.id.opponentMessageTextView)
                }
                messageText.text = message
                if(hideGameMessages && fromId == gameMessageId) messageTextView.isVisible = false
                else count++
                messagesLayout.addView(messageTextView)
            }

            // once the layout has updated its height, the messages at the bottom
            // of the layout are removed and they are added at the top
            messagesLayout.post {
                messagesLayout.removeViewAt(0)
                index = indexOfNextMessageToLoad
                count = 0
                while(count < nMessagesPerInterval) {
                    if(index < 0) break
                    indexOfNextMessageToLoad--
                    val messageContent = messages[index--].split("\t")
                    val message = messageContent[0]
                    val fromId = messageContent[1].toInt()
                    val layout = when(fromId) {
                        gameMessageId -> R.layout.widget_game_message
                        playerId -> R.layout.widget_player_message
                        else -> R.layout.widget_opponent_message
                    }
                    val messageTextView = activity.layoutInflater.inflate(layout, null)
                    val messageText: TextView = when(fromId) {
                        gameMessageId -> messageTextView.findViewById(R.id.gameMessageTextView)
                        playerId -> messageTextView.findViewById(R.id.playerMessageTextView)
                        else -> messageTextView.findViewById(R.id.opponentMessageTextView)
                    }
                    messageText.text = message
                    if(fromId == gameMessageId) gameMessages.add(messageTextView)
                    if(hideGameMessages && fromId == gameMessageId) messageTextView.isVisible = false
                    else count++
                    messagesLayout.addView(messageTextView, 0)
                    messagesLayout.removeViewAt(messagesLayout.size-1)
                }
                scrollView.scrollTo(0, messagesLayout.height - startingHeight)
                if(indexOfNextMessageToLoad < 0) allMessagesLoaded = true
                if(!allMessagesLoaded)
                    messagesLayout.addView(activity.layoutInflater.inflate(R.layout.widget_loading_messages, null), 0)
                loadingMessages = false
            }
        }
    }

    val exitButton = view.findViewById<LinearLayout>(R.id.bottomsheetOnlineMessagesExitButton)
    val sendButton = view.findViewById<Button>(R.id.onlineMessagesSendButton)
    val messageEntry = view.findViewById<EditText>(R.id.onlineMessagesTextField)
    val hideGameMessagesSwitch = view.findViewById<Switch>(R.id.hideGameMessagesSwitch)
    exitButton.setOnClickListener { bottomSheetDialog.cancel() }
    sendButton.setOnClickListener {
        val messageText = messageEntry.text.toString()
        if(messageText != "") {
            val message = "$messageText\t$playerId"
            messageEntry.setText("")
            addSentMessage(activity, selectedGame, message)
        }
    }

    messagesLayout.post {
        hideGameMessagesSwitch.isChecked = hideGameMessages
        hideGameMessagesSwitch.setOnCheckedChangeListener { _, isChecked ->
            hideGameMessages = isChecked
            for(gameMessageView in gameMessages) gameMessageView.isVisible = !isChecked
            messagesLayout.post {
                if(scrollView.height < messagesLayout.height) {
                    val params = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT)
                    params.gravity = Gravity.TOP
                    messagesLayout.layoutParams = params
                    scrollView.scrollTo(0, messagesLayout.height)
                } else {
                    val params = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT)
                    params.gravity = Gravity.BOTTOM
                    messagesLayout.layoutParams = params
                }
            }

            val sharedPreferences
                    = activity.getSharedPreferences(sharedPrefFilename, MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean(hideGameMessagesReference, isChecked)
            editor.apply()
        }
    }
    attemptDialogShow(bottomSheetDialog)
    return Result(scrollView, messagesLayout, bottomSheetDialog, gameMessages)
}

data class Result(val scrollView: ScrollView,
                  val messagesLayout: LinearLayout,
                  val bottomSheetDialog: BottomSheetDialog,
                  val gameMessages: ArrayList<View>)

@SuppressLint("InflateParams")
fun initConfirmQuitRoom(activity: AppCompatActivity, gameRoomKey: String,
                        roomCode: String, gameDetailsWidget: View? = null) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_confirm_cancel, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<TextView>(R.id.bottomsheetConfirmCancel).text =
        activity.getString(R.string.confirmDeletePrompt)
    val yesButton = view.findViewById<Button>(R.id.bottomsheetConfirmCancelYes)
    val noButton = view.findViewById<Button>(R.id.bottomsheetConfirmCancelNo)

    fun buttonClicked() {
        yesButton.isEnabled = false
        noButton.isEnabled = false
    }

    yesButton.setOnClickListener {
        buttonClicked()
        val fb = FirebaseDatabase.getInstance().reference
        fb.child("$gameRoomsPath/$gameRoomKey").setValue(null)
        fb.child("$messagesPath/$roomCode").setValue(null)
        if(gameDetailsWidget != null)
            activity.activeGamesLayout.removeView(gameDetailsWidget)
        bottomSheetDialog.cancel()
    }

    noButton.setOnClickListener {
        buttonClicked()
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

@SuppressLint("InflateParams")
fun initConfirmSameScreenRestart(activity: AppCompatActivity, selectedGame: String) {
    val bottomSheetDialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.bottomsheet_confirm_cancel, null)
    bottomSheetDialog.setContentView(view)
    bottomSheetDialog.setCancelable(false)

    view.findViewById<TextView>(R.id.bottomsheetConfirmCancel).text =
        activity.getString(R.string.confirmSameScreenRestartPrompt)
    val yesButton = view.findViewById<Button>(R.id.bottomsheetConfirmCancelYes)
    val noButton = view.findViewById<Button>(R.id.bottomsheetConfirmCancelNo)

    fun buttonClicked() {
        yesButton.isEnabled = false
        noButton.isEnabled = false
    }

    yesButton.setOnClickListener {
        restartSameScreenGame(activity, selectedGame)
        buttonClicked()
        bottomSheetDialog.cancel()
    }

    noButton.setOnClickListener {
        buttonClicked()
        bottomSheetDialog.cancel()
    }

    attemptDialogShow(bottomSheetDialog)
}

private fun attemptDialogShow(bottomSheetDialog: BottomSheetDialog) {
    try {
        bottomSheetDialog.show()
    } catch (e: WindowManager.BadTokenException) {
        e.printStackTrace()
    }
}

fun updateGameDate(activity: AppCompatActivity, gameRoomKey: String) {
    val fb = FirebaseDatabase.getInstance().reference
    val gameRoom = fb.child("$gameRoomsPath/$gameRoomKey")
    gameRoom.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(data: DataSnapshot) {
            if(data.value == null) return
            val game = data.child(gamePath).value.toString()
            val boardState = data.child(boardStatePath).value.toString()
            if(game == "null" || boardState == "null") return
            val dateString = if (boardState.contains("!!"))
                boardState.split("!!")[1]
            else {
                val boardStateContents = boardState.split("\t")
                boardStateContents[getNBoardStateIds(game)]
            }
            val newBoardState = boardState.replace(dateString, getDateString())
            gameRoom.child(boardStatePath).setValue(newBoardState)
        }

        override fun onCancelled(databaseError: DatabaseError) {
            displayMessageDialog(activity, activity.getString(R.string.unableToConnectPrompt))
        }
    })
}

private fun addSentMessage(activity: AppCompatActivity, selectedGame: String, message: String) {
    when(selectedGame) {
        battleshipRef -> (activity as BattleshipActivity).addSentMessage(message)
        connect4Ref -> (activity as Connect4Activity).addSentMessage(message)
        mastermindRef -> (activity as MastermindActivity).addSentMessage(message)
    }
}

private fun restartSameScreenGame(activity: AppCompatActivity, selectedGame: String) {
    when(selectedGame) {
        battleshipRef -> (activity as BattleshipActivity).initRestartSameScreenGame()
        connect4Ref -> (activity as Connect4Activity).initRestartSameScreenGame()
        mastermindRef -> (activity as MastermindActivity).initRestartSameScreenGame()
    }
}

private fun setupSameScreenOption(activity: AppCompatActivity, selectedGame: String) {
    when(selectedGame) {
        battleshipRef -> (activity as BattleshipActivity).setupForSameScreenOption()
        connect4Ref -> (activity as Connect4Activity).setupForSameScreenOption()
        mastermindRef -> (activity as MastermindActivity).setupForSameScreenOption()
    }
}

private fun setPlayerWithFirstMove(hostMovesFirst: Boolean, activity: AppCompatActivity,
                                   selectedGame: String, roomCode: String) {
    when(selectedGame) {
        battleshipRef -> (activity as BattleshipActivity).setPlayerWithFirstMove(hostMovesFirst, roomCode)
        connect4Ref -> (activity as Connect4Activity).setPlayerWithFirstMove(hostMovesFirst, roomCode)
        mastermindRef -> (activity as MastermindActivity).setPlayerWithFirstMove(hostMovesFirst, roomCode)
    }
}

private fun initRestartOnlineGame(activity: AppCompatActivity, selectedGame: String) {
    when (selectedGame) {
        battleshipRef -> (activity as BattleshipActivity).initRestartOnlineGame()
        connect4Ref -> (activity as Connect4Activity).initRestartOnlineGame()
        mastermindRef -> (activity as MastermindActivity).initRestartOnlineGame()
    }
}