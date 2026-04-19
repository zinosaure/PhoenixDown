package com.swordfish.lemuroid.app.tv.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.room.Room
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.smb.SmbClient
import com.swordfish.lemuroid.lib.storage.smb.SmbCredentials
import com.swordfish.lemuroid.lib.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV Activity for editing a game's system assignment.
 * Moves the file to the new system folder and updates the database.
 */
class TVGameEditActivity : FragmentActivity() {
    
    companion object {
        const val EXTRA_GAME = "extra_game"
        
        fun createIntent(context: Context, game: Game): Intent {
            return Intent(context, TVGameEditActivity::class.java).apply {
                putExtra(EXTRA_GAME, game)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val game = intent.getSerializableExtra(EXTRA_GAME) as? Game
        if (game == null) {
            finish()
            return
        }
        
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, SystemSelectionFragment.newInstance(game), android.R.id.content)
        }
    }
    
    class SystemSelectionFragment : GuidedStepSupportFragment() {
        
        private val game: Game by lazy {
            arguments?.getSerializable(ARG_GAME) as? Game 
                ?: throw IllegalArgumentException("Game is required")
        }
        
        companion object {
            private const val ARG_GAME = "arg_game"
            private const val ACTION_CANCEL = -1L
            
            fun newInstance(game: Game): SystemSelectionFragment {
                return SystemSelectionFragment().apply {
                    arguments = Bundle().apply {
                        putSerializable(ARG_GAME, game)
                    }
                }
            }
        }
        
        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            return GuidanceStylist.Guidance(
                getString(R.string.game_edit_change_system),
                game.title,
                game.fileName,
                null
            )
        }
        
        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            val systems = SystemID.values().sortedBy { it.dbname }
            
            systems.forEachIndexed { index, systemId ->
                val isCurrentSystem = systemId.dbname == game.systemId
                actions.add(
                    GuidedAction.Builder(requireContext())
                        .id(index.toLong())
                        .title(getSystemDisplayName(systemId))
                        .description(if (isCurrentSystem) "✓ Sistema actual" else "")
                        .checked(isCurrentSystem)
                        .build()
                )
            }
        }
        
        override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_CANCEL)
                    .title("Cancel")
                    .build()
            )
        }
        
        override fun onGuidedActionClicked(action: GuidedAction) {
            if (action.id == ACTION_CANCEL) {
                requireActivity().finish()
                return
            }
            
            val systems = SystemID.values().sortedBy { it.dbname }
            val selectedIndex = action.id.toInt()
            if (selectedIndex >= 0 && selectedIndex < systems.size) {
                val newSystemId = systems[selectedIndex].dbname
                
                if (newSystemId != game.systemId) {
                    moveGameToSystem(newSystemId)
                } else {
                    requireActivity().finish()
                }
            }
        }
        
        private fun moveGameToSystem(newSystemId: String) {
            val context = requireContext()
            val activity = requireActivity()
            
            Toast.makeText(context, "Moving file...", Toast.LENGTH_SHORT).show()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Get or create database instance
                    val retrogradeDb = Room.databaseBuilder(
                        context.applicationContext,
                        RetrogradeDatabase::class.java,
                        RetrogradeDatabase.DB_NAME
                    ).build()
                    
                    val prefs = SharedPreferencesHelper.getSharedPreferences(context)
                    val server = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, null)
                    val share = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, null)
                    val basePath = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, "") ?: ""
                    val username = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, null)
                    val password = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, null)
                    
                    // Parse current file path from URI (remove share prefix)
                    val currentUri = Uri.parse(game.fileUri)
                    val uriPath = currentUri.path?.removePrefix("/") ?: game.fileName
                    val currentPath = if (share != null && uriPath.startsWith("$share/")) {
                        uriPath.removePrefix("$share/")
                    } else {
                        uriPath
                    }
                    
                    // Build new path: basePath/newSystemId/filename
                    val fileName = game.fileName
                    val newPath = if (basePath.isNotEmpty()) {
                        "${basePath.removePrefix("/")}/$newSystemId/$fileName"
                    } else {
                        "$newSystemId/$fileName"
                    }
                    
                    // Move file on SMB
                    if (!server.isNullOrBlank() && !share.isNullOrBlank()) {
                        val credentials = if (!username.isNullOrBlank()) {
                            SmbCredentials(username, password ?: "")
                        } else null
                        
                        val smbClient = SmbClient()
                        val moveResult = smbClient.moveFile(
                            server = server,
                            share = share,
                            sourcePath = currentPath,
                            destPath = newPath,
                            credentials = credentials
                        )
                        
                        if (moveResult.isSuccess) {
                            // Delete old game record - scanner will create new one on rescan
                            retrogradeDb.gameDao().delete(listOf(game))
                            retrogradeDb.close()
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "File moved to $newSystemId. Rescan to see the change.",
                                    Toast.LENGTH_LONG
                                ).show()
                                activity.finish()
                            }
                        } else {
                            retrogradeDb.close()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Move failed: ${moveResult.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                                activity.finish()
                            }
                        }
                    } else {
                        // No SMB config - just update systemId in database
                        val updatedGame = game.copy(systemId = newSystemId)
                        retrogradeDb.gameDao().update(updatedGame)
                        retrogradeDb.close()
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "System updated", Toast.LENGTH_SHORT).show()
                            activity.finish()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        activity.finish()
                    }
                }
            }
        }
        
        private fun getSystemDisplayName(systemId: SystemID): String {
            return when (systemId.dbname) {
                "atari2600" -> "Atari 2600"
                "nes" -> "NES"
                "snes" -> "Super Nintendo"
                "sms" -> "Master System"
                "genesis" -> "Genesis/Mega Drive"
                "segacd" -> "Sega CD"
                "gg" -> "Game Gear"
                "gb" -> "Game Boy"
                "gbc" -> "Game Boy Color"
                "gba" -> "Game Boy Advance"
                "n64" -> "Nintendo 64"
                "nds" -> "Nintendo DS"
                "psx" -> "PlayStation"
                "psp" -> "PlayStation Portable"
                "fbneo" -> "FBNeo (Arcade)"
                "mame2003_plus" -> "MAME 2003+"
                "pce" -> "PC Engine"
                "ngp" -> "Neo Geo Pocket"
                "ngc" -> "Neo Geo Pocket Color"
                "ws" -> "WonderSwan"
                "wsc" -> "WonderSwan Color"
                "dos" -> "DOS"
                "lynx" -> "Atari Lynx"
                "atari7800" -> "Atari 7800"
                "3ds" -> "Nintendo 3DS"
                else -> systemId.dbname.uppercase()
            }
        }
    }
}
