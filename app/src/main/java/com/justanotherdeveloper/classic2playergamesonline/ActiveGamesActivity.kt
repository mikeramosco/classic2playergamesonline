package com.justanotherdeveloper.classic2playergamesonline

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_active_games.*
import java.util.*
import kotlin.collections.ArrayList

class ActiveGamesActivity : AppCompatActivity() {

    private lateinit var activeGamesData: ArrayList<DataSnapshot>
    private val returnFromGameCode = 1
    private var gameoversFirst = true
    private var playerId = -1
    var activityIsActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_games)
        initProgressBar()
        initValues()
        getActiveGames()
    }

    @Suppress("UNUSED_PARAMETER")
    fun homeButtonFromActiveGamesClicked(view: View) {
        finish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun settingsButtonFromActiveGamesClicked(view: View?) {
        val openPage = Intent(this, SettingsActivity::class.java)
        startActivity(openPage)
    }

    private fun initProgressBar() {
        activeGamesProgressBar.visibility = View.VISIBLE
    }

    private fun initActiveGamesCornerButtons() {
        initGameoversFirstSwitch()
        initRefreshButton()
    }

    private fun initRefreshButton() {
        refreshButton.setOnClickListener {
            activeGamesProgressBar.isVisible = true
            activeGamesLayout.removeAllViews()
            getActiveGames()
        }
    }

    private fun initGameoversFirstSwitch() {
        gameoversFirstSwitch.isChecked = gameoversFirst
        gameoversFirstSwitch.setOnCheckedChangeListener { _, isChecked ->
            gameoversFirst = isChecked
            activeGamesLayout.removeAllViews()
            orderActiveGames()

            val sharedPreferences
                    = getSharedPreferences(sharedPrefFilename, MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean(gameoversFirstRef, isChecked)
            editor.apply()
        }
    }

    private fun initValues() {
        playerId = getSharedPreferences(sharedPrefFilename, MODE_PRIVATE).getInt(playerIdRef, 1)
        gameoversFirst = getSharedPreferences(sharedPrefFilename, MODE_PRIVATE).getBoolean(
            gameoversFirstRef, false)
    }

    private fun getActiveGames() {
        activeGamesData = ArrayList()
        val fb = FirebaseDatabase.getInstance().reference
        val hostedActiveGames = fb.child(gameRoomsPath).orderByChild(hostIdPath).equalTo(playerId.toDouble())
        val activity = this
        hostedActiveGames.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                val dataIterator = data.children.iterator()
                while(dataIterator.hasNext()) activeGamesData.add(dataIterator.next())
                getJoinedActiveGames()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive) displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    private fun getJoinedActiveGames() {
        val fb = FirebaseDatabase.getInstance().reference
        val joinedActiveGames = fb.child(gameRoomsPath).orderByChild(clientIdPath).equalTo(playerId.toDouble())
        val activity = this
        joinedActiveGames.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(data: DataSnapshot) {
                val dataIterator = data.children.iterator()
                while(dataIterator.hasNext()) activeGamesData.add(dataIterator.next())
                orderActiveGames()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive) displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    private fun orderActiveGames() {
        val orderedActiveGamesData = ArrayList<String>()
        for(data in activeGamesData) {

            // get data
            val roomCode = data.child(roomCodePath).value.toString()
            val game = data.child(gamePath).value.toString()
            val boardState = data.child(boardStatePath).value.toString()
            val dateString: String
            val isPlayersTurn: Boolean
            val gameRoomKey = data.key!!
            val playerIsHost = data.child(hostIdPath).value.toString().toInt() == playerId
            var idOfWinnerIfGameOver = -1
            val opponentId = if(playerIsHost) data.child(clientIdPath).value.toString()
            else data.child(hostIdPath).value.toString()
            if(boardState.contains("!!")) {
                val hostMovesFirst = data.child(hostMovesFirstPath).value.toString()
                isPlayersTurn = when {
                    boardState.contains("&&") ->
                        playerId != boardState.split("&&")[1]
                            .split("!!")[0].toInt()
                    hostMovesFirst == "null" -> playerIsHost
                    game == battleshipRef -> true
                    hostMovesFirst.contains("restart") ->
                        hostMovesFirst.split("\t")[1].toBoolean()
                    else -> if(playerIsHost) hostMovesFirst.toBoolean()
                    else !hostMovesFirst.toBoolean()
                }
                dateString = boardState.split("!!")[1]
            } else {
                val nBoardStateIds = getNBoardStateIds(game)
                if(nBoardStateIds == sentinel) return
                val boardStateContents = boardState.split("\t")
                val lastContent = boardStateContents[boardStateContents.lastIndex]
                if(!lastContent.contains(":")) idOfWinnerIfGameOver = lastContent.toInt()
                var idOfPlayersTurn = boardStateContents[nBoardStateIds+1]
                if(idOfPlayersTurn.contains(":"))
                    idOfPlayersTurn = idOfPlayersTurn.split(":")[0]
                isPlayersTurn = idOfPlayersTurn.toInt() == playerId
                dateString = boardStateContents[nBoardStateIds]
            }

            // order data
            val activeGamesDataString = "$roomCode\t$game\t$isPlayersTurn\t$dateString" +
                    "\t$gameRoomKey\t$opponentId\t$playerIsHost\t$idOfWinnerIfGameOver"
            if(orderedActiveGamesData.size == 0) orderedActiveGamesData.add(activeGamesDataString)
            else {
                val gameDateContents = dateString.split(":")
                val gameDateCalendar = Calendar.getInstance()
                gameDateCalendar.set(gameDateContents[2].toInt(),
                    gameDateContents[0].toInt(), gameDateContents[1].toInt())
                for((index, gameData) in orderedActiveGamesData.withIndex()) {
                    val dataContents = gameData.split("\t")
                    val dataIsPlayersTurn = dataContents[2].toBoolean()
                    val dataIdOfWinner = dataContents[7].toInt()

                    if(gameoversFirst) {
                        // game over games on top
                        if(idOfWinnerIfGameOver != -1 && dataIdOfWinner == -1) {
                            orderedActiveGamesData.add(index, activeGamesDataString)
                            break
                        }
                        if(dataIsPlayersTurn == isPlayersTurn &&
                            idOfWinnerIfGameOver == -1 && dataIdOfWinner == -1 ||
                            idOfWinnerIfGameOver != -1 && dataIdOfWinner != -1) {
                            val dataDateContents = dataContents[3].split(":")
                            val dataDateCalendar = Calendar.getInstance()
                            dataDateCalendar.set(dataDateContents[2].toInt(),
                                dataDateContents[0].toInt(), dataDateContents[1].toInt())
                            if (dataDateCalendar.time <= gameDateCalendar.time) {
                                orderedActiveGamesData.add(index, activeGamesDataString)
                                break
                            }
                        }
                        if(isPlayersTurn && !dataIsPlayersTurn &&
                            idOfWinnerIfGameOver == -1 && dataIdOfWinner == -1) {
                            orderedActiveGamesData.add(index, activeGamesDataString)
                            break
                        }
                        if(index == orderedActiveGamesData.lastIndex)
                            orderedActiveGamesData.add(activeGamesDataString)
                    } else {
                        // game over games on bottom
                        if(isPlayersTurn && !dataIsPlayersTurn &&
                            idOfWinnerIfGameOver == -1 && dataIdOfWinner == -1 ||
                            idOfWinnerIfGameOver == -1 && dataIdOfWinner != -1) {
                            orderedActiveGamesData.add(index, activeGamesDataString)
                            break
                        }
                        if(dataIsPlayersTurn == isPlayersTurn &&
                            idOfWinnerIfGameOver == -1 && dataIdOfWinner == -1 ||
                            idOfWinnerIfGameOver != -1 && dataIdOfWinner != -1) {
                            val dataDateContents = dataContents[3].split(":")
                            val dataDateCalendar = Calendar.getInstance()
                            dataDateCalendar.set(dataDateContents[2].toInt(),
                                dataDateContents[0].toInt(), dataDateContents[1].toInt())
                            if (dataDateCalendar.time <= gameDateCalendar.time) {
                                orderedActiveGamesData.add(index, activeGamesDataString)
                                break
                            }
                        }
                        if(index == orderedActiveGamesData.lastIndex)
                            orderedActiveGamesData.add(activeGamesDataString)
                    }
                }
            }
        }
        displayActiveGames(orderedActiveGamesData)
    }

    @SuppressLint("InflateParams")
    private fun displayActiveGames(orderedActiveGamesData: ArrayList<String>) {
        val activeGamesButtons = ArrayList<Button>()
        for(data in orderedActiveGamesData) {
            val dataContents = data.split("\t")
            val roomCode = dataContents[0]
            val game = dataContents[1]
            val isPlayersMove = dataContents[2].toBoolean()
            val dateString = dataContents[3]
            val gameRoomKey = dataContents[4]
            val opponentId = dataContents[5].toInt()
            val playerIsHost = dataContents[6].toBoolean()
            val idOfWinner = dataContents[7].toInt()

            if(playerIsHost && opponentId == 0) {
                val fb = FirebaseDatabase.getInstance().reference
                fb.child("$gameRoomsPath/$gameRoomKey").setValue(null)
            } else {
                val gameDetailsWidget =
                    layoutInflater.inflate(R.layout.widget_active_game_details, null)
                gameDetailsWidget.findViewById<TextView>(R.id.detailsRoomCode).text =
                    getString(R.string.detailsRoomCodeText, roomCode)
                gameDetailsWidget.findViewById<TextView>(R.id.detailsGame).text =
                    getAppGameName(game)
                val detailsTurnTextView =
                    gameDetailsWidget.findViewById<TextView>(R.id.detailsTurn)
                if (idOfWinner != -1) {
                    detailsTurnTextView.text = when (idOfWinner) {
                        playerId -> getString(R.string.gameOverYouWonText)
                        0 -> getString(R.string.gameOverItsATieText)
                        else -> getString(R.string.gameOverTheyWonText)
                    }
                    detailsTurnTextView.setTextColor(ContextCompat.getColor(applicationContext,
                        R.color.darkGray))
                } else if (!isPlayersMove) {
                    detailsTurnTextView.text = getString(R.string.itsTheirTurnText)
                    detailsTurnTextView.setTextColor(ContextCompat.getColor(applicationContext,
                        R.color.colorPrimary))
                }

                val effectiveDate = inactivityDeletionEffectiveDate.dateStringToCalendar()
                val dateAsCalendar = dateString.dateStringToCalendar()
                val inactivityDeletionInEffect = getTodaysDate().comesAfter(effectiveDate)
                val dateToUse = if(inactivityDeletionInEffect
                    && effectiveDate.comesAfter(dateAsCalendar))
                    inactivityDeletionEffectiveDate else dateString
                val nDaysSinceLastMove = getNDaysSinceLastMove(dateToUse)
                if(inactivityDeletionInEffect && nDaysSinceLastMove > inactiveDaysThreshold)
                    gameDetailsWidget.visibility = View.GONE
                val lastMovePrompt = getLastMovePrompt(nDaysSinceLastMove)
                gameDetailsWidget.findViewById<TextView>(R.id.detailsLastMove).text = lastMovePrompt

                val resumeButton =
                    gameDetailsWidget.findViewById<Button>(R.id.gameDetailsResumeButton)
                val quitButton =
                    gameDetailsWidget.findViewById<Button>(R.id.gameDetailsQuitButton)
                val progressBar =
                    gameDetailsWidget.findViewById<ProgressBar>(R.id.resumeProgressBar)

                activeGamesButtons.add(resumeButton)
                activeGamesButtons.add(quitButton)

                resumeButton.setOnClickListener {
                    progressBar.isVisible = true
                    resumeButton.isVisible = false
                    for(button in activeGamesButtons) button.isEnabled = false
                    val roomValues = "$playerIsHost\t$playerId\t$opponentId\t$gameRoomKey\t$roomCode"
                    resumeGame(game, roomValues)
                }

                quitButton.setOnClickListener {
                    initConfirmQuitRoom(this, gameRoomKey, roomCode, gameDetailsWidget)
                }

                activeGamesLayout.addView(gameDetailsWidget)
            }
        }
        activeGamesLayout.post {
            activeGamesProgressBar.isVisible = false
            initActiveGamesCornerButtons()
        }
    }

    private fun openResumeGameActivity(activity: AppCompatActivity, roomValues: String) {
        val openPage = Intent(this, activity::class.java)
        openPage.putExtra(setupModeRef, resumeSetupRef)
        openPage.putExtra(roomValuesRef, roomValues)
        startActivityForResult(openPage, returnFromGameCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == returnFromGameCode) {
            when(data?.getStringExtra(openNextRef)) {
                "Active Games" -> setResult(Activity.RESULT_OK, data)
            }
            finish()
        }
    }

    private fun getLastMovePrompt(nDaysSinceLastMove: Int): String {
        val suffix = when(nDaysSinceLastMove) {
            0 -> getString(R.string.todayText)
            1 -> getString(R.string.yesterdayText)
            else -> getString(R.string.daysAgoText, nDaysSinceLastMove.toString())
        }
        return getString(R.string.lastMoveText, suffix)
    }

    private fun resumeGame(selectedGame: String, roomValues: String) {
        when(selectedGame) {
            battleshipRef -> openResumeGameActivity(BattleshipActivity(), roomValues)
            connect4Ref -> openResumeGameActivity(Connect4Activity(), roomValues)
            mastermindRef -> openResumeGameActivity(MastermindActivity(), roomValues)
        }
    }

    private fun getAppGameName(game: String): String {
        return when(game) {
            battleshipRef -> getString(R.string.battleshipText)
            connect4Ref -> getString(R.string.connect4Text)
            mastermindRef -> getString(R.string.mastermindText)
            else -> getString(R.string.battleshipText)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityIsActive = false
    }
}
