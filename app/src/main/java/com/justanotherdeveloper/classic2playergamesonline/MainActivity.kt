package com.justanotherdeveloper.classic2playergamesonline

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

//    private lateinit var mAdView: AdView
    private val returnFromGameCode = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        initAdView()
        initSpinnerListener()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == returnFromGameCode) {
            homeProgressBar.isVisible = false
            startGameButton.isEnabled = true
            activeRoomsButtonMain.isEnabled = true
            changeStyleButton.isEnabled = true
            joinRoomButtonMain.isEnabled = true

            when(data?.getStringExtra(openNextRef)) {
                "Active Games" -> openActivity(ActiveGamesActivity())
            }
        }
    }

    private fun initSpinnerListener() {
        gameListSpinnerMain.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(parentView: AdapterView<*>) { }
            override fun onItemSelected(
                parentView: AdapterView<*>,
                selectedItemView: View,
                position: Int,
                id: Long
            ) {
                gameCoverImage.setImageResource(getNextGameCoverImage(position))
            }
        }
    }

    private fun getNextGameCoverImage(position: Int): Int {
        return when(position) {
            0 -> R.drawable.battleship_cover
            1 -> R.drawable.connect_four_cover
            2 -> R.drawable.mastermind_cover
            else -> R.drawable.battleship_cover
        }

//        return when(position) {
//            0 -> R.drawable.battleship_cover
//            1 -> R.drawable.connect_four_cover
//            2 -> R.drawable.mancala_cover
//            3 -> R.drawable.mastermind_cover
//            4 -> R.drawable.othello_cover
//            else -> R.drawable.battleship_cover
//        }
    }

    private fun getNextGameIndex(isPrevious: Boolean): Int {
        val listOfGames = resources.getStringArray(R.array.listOfGamesArray).toList()
        var gameIndex = gameListSpinnerMain.selectedItemPosition
        if(isPrevious) gameIndex--
        else gameIndex++
        if(gameIndex < 0) gameIndex = listOfGames.size - 1
        else if(gameIndex > listOfGames.size - 1) gameIndex = 0
        return gameIndex
    }

    private fun openActivity(activity: AppCompatActivity) {
        buttonClicked()
        val openPage = Intent(this, activity::class.java)
        startActivityForResult(openPage, returnFromGameCode)
    }

    private fun openNewGameActivity(activity: AppCompatActivity) {
        buttonClicked()
        val openPage = Intent(this, activity::class.java)
        openPage.putExtra(setupModeRef, newSetupRef)
        startActivityForResult(openPage, returnFromGameCode)
    }

    private fun buttonClicked() {
        startGameButton.isEnabled = false
        activeRoomsButtonMain.isEnabled = false
        changeStyleButton.isEnabled = false
        joinRoomButtonMain.isEnabled = false
    }

    @Suppress("UNUSED_PARAMETER")
    fun leftArrowClicked(view: View) {
        val nextGameIndex = getNextGameIndex(true)
        gameCoverImage.setImageResource(getNextGameCoverImage(nextGameIndex))
        gameListSpinnerMain.setSelection(nextGameIndex)
    }

    @Suppress("UNUSED_PARAMETER")
    fun rightArrowClicked(view: View) {
        val nextGameIndex = getNextGameIndex(false)
        gameCoverImage.setImageResource(getNextGameCoverImage(nextGameIndex))
        gameListSpinnerMain.setSelection(nextGameIndex)
    }

    @Suppress("UNUSED_PARAMETER")
    fun startGameClicked(view: View?) {
        homeProgressBar.isVisible = true
        when(gameListSpinnerMain.selectedItem.toString()) {
            getString(R.string.battleshipText) -> openNewGameActivity(BattleshipActivity())
            getString(R.string.connect4Text) -> openNewGameActivity(Connect4Activity())
            getString(R.string.mastermindText) -> openNewGameActivity(MastermindActivity())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun activeGamesClicked(view: View?) {
        homeProgressBar.isVisible = true
        openActivity(ActiveGamesActivity())
    }

    @Suppress("UNUSED_PARAMETER")
    fun changeStyleClicked(view: View?) {
        openActivity(ChangeStyleActivity())
    }

    @Suppress("UNUSED_PARAMETER")
    fun joinRoomClicked(view: View?) {
        openActivity(JoinRoomActivity())
    }

    @Suppress("UNUSED_PARAMETER")
    fun settingsClicked(view: View?) {
        openActivity(SettingsActivity())
    }

//    private fun initAdView() {
//        MobileAds.initialize(this) { }
//        mAdView = findViewById(R.id.homeAd)
//        val adRequest = AdRequest.Builder().build()
//        mAdView.loadAd(adRequest)
//
//        mAdView.isVisible = false
//        mAdView.adListener = object: AdListener() {
//            override fun onAdLoaded() {
//                mAdView.isVisible = true
//                super.onAdLoaded()
//            }
//
//            override fun onAdFailedToLoad(errorCode: Int) {
//                Log.d("dtag", errorCode.toString())
//            }
//        }
//    }
}
