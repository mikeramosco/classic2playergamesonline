package com.justanotherdeveloper.classic2playergamesonline

import java.util.*

const val inactiveDaysThreshold = 60
const val inactivityDeletionEffectiveDate = "1:1:2023"

const val battleshipRef = "battleship"
const val connect4Ref = "connect4"
const val mastermindRef = "mastermind"

const val setupModeRef = "setupMode"

const val newSetupRef = "newSetup"
const val joinSetupRef = "joinSetup"
const val resumeSetupRef = "resumeSetup"
const val roomCodeRef = "firebaseRoom"
const val roomValuesRef = "roomValues"
const val openNextRef = "openNext"

const val sentinel = -1
const val gameMessageId = 0

const val horizontalID = "H"
const val verticalID = "V"
const val oceanID = "O"
const val carrierID = "C"
const val battleshipID = "B"
const val submarineID = "S"
const val largeShipID = "L"
const val tinyShipID = "T"

const val battleshipBoardLength = 10

const val connect4BoardRows = 6
const val connect4BoardCols = 7

const val mastermindLeftBoardRows = 6
const val mastermindRightBoardRows = 4
const val mastermindBoardCols = 4

const val redR = 255
const val redG = 0
const val redB = 0

const val yellowR = 255
const val yellowG = 255
const val yellowB = 0

const val winRedR = 175
const val winRedG = 0
const val winRedB = 0

const val winYellowR = 175
const val winYellowG = 175
const val winYellowB = 0

const val mastermindAnimateFrames = 15
const val mastermindFPS = 100

const val connect4AnimateFrames = 15
const val connect4FPS = 120
const val connect4DropFPS = 150

const val battleshipAnimateFrames = 15
const val battleshipFPS = 100

const val turnChangeDelay = 1000.toLong()
const val dialogDelay = 350.toLong()
const val listenerDelay = 100.toLong()
//const val unselectOptionDelay = 1000.toLong()

const val nMessagesPerInterval = 50

const val maxCharInMessage = 70

const val sharedPrefFilename = "sharedPreferences"
const val playerIdRef = "userId"
const val gameoversFirstRef = "gameoversFirst"

const val hideBattleshipMessagesRef = "hideBattleshipMessages"
const val hideConnect4MessagesRef = "hideConnect4Messages"
const val hideMastermindMessagesRef = "hideMastermindMessagesRef"

const val playerIdsPath = "userids"
const val idPath = "id"
const val messagesPath = "messages"
const val messagePath = "message"
const val gameRoomsPath = "gamerooms"
const val roomCodePath = "roomcode"
const val hostIdPath = "hostid"
const val clientIdPath = "clientid"
const val gamePath = "game"
const val hostMovesFirstPath = "hostmovesfirst"
const val boardStatePath = "boardstate"

fun getNumberToDivide(text: String): Float {
    for (ch in "gjpqy") if (text.contains(ch)) return 4f; return 2f
}

fun getDateString(): String {
    val month = Calendar.getInstance().get(Calendar.MONTH) + 1
    val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val year = Calendar.getInstance().get(Calendar.YEAR)

    return "$month:$day:$year"
}