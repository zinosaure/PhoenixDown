package com.swordfish.lemuroid.app.shared

import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.library.LemuroidLibrary
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameInteractor(
    private val activity: BusyActivity,
    private val retrogradeDb: RetrogradeDatabase,
    private val useLeanback: Boolean,
    private val shortcutsGenerator: ShortcutsGenerator,
    private val gameLauncher: GameLauncher,
    private val lemuroidLibrary: LemuroidLibrary,
) {
    fun onGamePlay(game: Game) {
        if (activity.isBusy()) {
            activity.activity().displayToast(R.string.game_interactory_busy)
            return
        }
        gameLauncher.launchGameAsync(activity.activity(), game, true, useLeanback)
    }

    fun onGameRestart(game: Game) {
        if (activity.isBusy()) {
            activity.activity().displayToast(R.string.game_interactory_busy)
            return
        }
        gameLauncher.launchGameAsync(activity.activity(), game, false, useLeanback)
    }

    fun onFavoriteToggle(
        game: Game,
        isFavorite: Boolean,
    ) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(isFavorite = isFavorite))
        }
    }

    fun onCreateShortcut(game: Game) {
        GlobalScope.launch {
            shortcutsGenerator.pinShortcutForGame(game)
        }
    }

    fun supportShortcuts(): Boolean {
        return shortcutsGenerator.supportShortcuts()
    }
    
    fun updateGame(game: Game) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game)
        }
    }

    fun deleteGame(game: Game) {
        GlobalScope.launch {
            lemuroidLibrary.deleteGame(game)
        }
    }
    
    fun onDeleteGame(game: Game) {
        val context = activity.activity()
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.game_context_menu_delete)
            .setMessage(context.getString(R.string.game_delete_confirmation, game.title))
            .setPositiveButton(R.string.ok) { _, _ ->
                deleteGame(game)
                context.displayToast(R.string.game_deleted)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun deleteGames(games: List<Game>) {
        GlobalScope.launch {
            games.forEach { lemuroidLibrary.deleteGame(it) }
        }
    }

    fun onRenameGame(game: Game) {
        val context = activity.activity()
        val editText = android.widget.EditText(context).apply {
            setText(game.title)
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            val padding = (20 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.game_context_menu_edit)
            .setView(editText)
            .setPositiveButton(R.string.game_edit_save) { _, _ ->
                val newTitle = editText.text.toString()
                if (newTitle.isNotBlank() && newTitle != game.title) {
                    updateGame(game.copy(title = newTitle))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    fun onChangeSystem(game: Game) {
        if (useLeanback) {
            // TV mode - launch TVGameEditActivity
            val intent = com.swordfish.lemuroid.app.tv.settings.TVGameEditActivity.createIntent(
                activity.activity(), 
                game
            )
            activity.activity().startActivityForResult(intent, REQUEST_CHANGE_SYSTEM)
        }
        // Mobile mode is handled differently (via GameEditDialog in Compose)
    }
    
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (requestCode == REQUEST_CHANGE_SYSTEM && resultCode == android.app.Activity.RESULT_OK) {
            val updatedGame = data?.getSerializableExtra("extra_game") as? Game
            if (updatedGame != null) {
                updateGame(updatedGame)
            }
        }
    }
    
    companion object {
        const val REQUEST_CHANGE_SYSTEM = 1001
    }
}
