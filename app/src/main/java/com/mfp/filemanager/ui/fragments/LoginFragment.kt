package com.mfp.filemanager.ui.fragments

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.compose.content
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.mfp.filemanager.feature.drive.GoogleLoginHelper

class LoginFragment : Fragment() {


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = content {

        var isLoggedIn by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val googleClient  = remember { GoogleLoginHelper.getGoogleSignInClient(context) }
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val intent = result.data
                    if (result.data != null) {
                        val task: Task<GoogleSignInAccount> =
                            GoogleSignIn.getSignedInAccountFromIntent(intent)

                    } else {
                        Toast.makeText(context, "Google Login Error!", Toast.LENGTH_LONG).show()
                    }
                }
            }

        LaunchedEffect(Unit) {
            isLoggedIn = GoogleLoginHelper.getLastSignedInAccount(context) != null
        }
        Mdc3Theme {
            Scaffold { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    if (!isLoggedIn) {
                        Button(modifier = Modifier.align(Alignment.Center), onClick = {
                            launcher.launch(googleClient.signInIntent)
                        }) {
                            Text("Login with Google with ❤️")
                        }
                    } else {

                    }
                }
            }

        }
    }
}