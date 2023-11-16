package com.justanotherdeveloper.classic2playergamesonline

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import kotlinx.android.synthetic.main.activity_join_room.*
import android.text.InputFilter
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class JoinRoomActivity : AppCompatActivity() {

    private val returnFromGameCode = 1
    var activityIsActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_room)
        createFilterForRoomCodeEntryTextField()
    }

    private fun createFilterForRoomCodeEntryTextField() {
        roomCodeEntryField.filters = arrayOf<InputFilter>(InputFilter.AllCaps())
        roomCodeEntryField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int,
                                           count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence, start: Int,
                                       before: Int, count: Int) {
                if(roomCodeEntryField.text.length > 4) {
                    var selection = roomCodeEntryField.selectionStart
                    roomCodeEntryField.setText(roomCodeEntryField.text.substring(0, 4))
                    if(selection > 3) selection = roomCodeEntryField.text.length
                    roomCodeEntryField.setSelection(selection)
                } else adjustTextField()
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun adjustTextField() {
        val text = roomCodeEntryField.text.toString()
        if(text != "") for((index, ch) in text.withIndex())
            if(!"ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".contains(ch)) {
                roomCodeEntryField.setText(text.substring(0, index) + text.substring(index+1, text.length))
                roomCodeEntryField.setSelection(index)
                break
            }
    }

    @Suppress("UNUSED_PARAMETER")
    fun homeButtonFromJoinRoomClicked(view: View) {
        finish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun settingsButtonFromJoinRoomClicked(view: View) {
        val openPage = Intent(this, SettingsActivity::class.java)
        startActivity(openPage)
    }

    @Suppress("UNUSED_PARAMETER")
    fun joinRoomButtonClicked(view: View) {
        joinRoomButtonEntry.isVisible = false
        joinRoomProgressBar.isVisible = true
        val sharedPreferences = getSharedPreferences(sharedPrefFilename, MODE_PRIVATE)
        val playerId = sharedPreferences.getInt(playerIdRef, 0)
        if(playerId == 0) createPlayerIdToJoinRoom()
        else joinRoom(playerId)
    }

    private fun createPlayerIdToJoinRoom() {
        val fb = FirebaseDatabase.getInstance().reference
        val playerIds = fb.child(playerIdsPath)
        val ids = playerIds.orderByChild(idPath)
        val activity = this
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
                        = getSharedPreferences(sharedPrefFilename, MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putInt(playerIdRef, playerId)
                editor.apply()
                val newGameRoom = playerIds.push()
                newGameRoom.child(idPath).setValue(playerId)
                joinRoom(playerId)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                if(activityIsActive) displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
            }
        })
    }

    private fun enableJoinRoomButton() {
        joinRoomButtonEntry.isVisible = true
        joinRoomProgressBar.isVisible = false
    }

    private fun joinRoom(playerId: Int) {
        val roomCodeEntered = roomCodeEntryField.text.toString()
        if(roomCodeEntered.length == 4) {
            val fb = FirebaseDatabase.getInstance().reference
            val gameRooms = fb.child(gameRoomsPath)
            val roomCodes = gameRooms.orderByChild(roomCodePath).equalTo(roomCodeEntered)
            val activity = this
            roomCodes.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(data: DataSnapshot) {
                    val dataIterator = data.children.iterator()
                    if(dataIterator.hasNext()) {
                        val gameRoom = dataIterator.next()
                        val selectedGame = gameRoom.child(gamePath).value.toString()
                        val clientId = gameRoom.child(clientIdPath).value.toString().toInt()
                        if(clientId == 0) {
                            gameRoom.ref.child(clientIdPath).setValue(playerId)
                            openRoom(selectedGame, roomCodeEntered)
                        } else {
                            enableJoinRoomButton()
                            if(activityIsActive)
                                displayMessageDialog(activity, getString(R.string.roomAlreadyHasClientPrompt))
                        }
                    } else {
                        enableJoinRoomButton()
                        if(activityIsActive)
                            displayMessageDialog(activity, getString(R.string.noRoomFoundPrompt))
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    enableJoinRoomButton()
                    if(activityIsActive)
                        displayMessageDialog(activity, getString(R.string.unableToConnectPrompt))
                }
            })
        } else {
            enableJoinRoomButton()
            if(activityIsActive)
                displayMessageDialog(this, getString(R.string.invalidRoomCodePrompt))
        }
    }

    private fun openRoom(selectedGame: String, roomCode: String) {
        when(selectedGame) {
            battleshipRef -> openJoinGameActivity(BattleshipActivity(), roomCode)
            connect4Ref -> openJoinGameActivity(Connect4Activity(), roomCode)
            mastermindRef -> openJoinGameActivity(MastermindActivity(), roomCode)
        }
    }

    private fun openJoinGameActivity(activity: AppCompatActivity, roomCode: String) {
        hideKeyboard()
        val openPage = Intent(this, activity::class.java)
        openPage.putExtra(setupModeRef, joinSetupRef)
        openPage.putExtra(roomCodeRef, roomCode)
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

//    private fun Fragment.hideKeyboard() {
//        view?.let { activity?.hideKeyboard(it) }
//    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityIsActive = false
    }
}
