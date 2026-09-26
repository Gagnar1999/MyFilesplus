package com.mfp.filemanager.feature.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.services.drive.DriveScopes
import com.mfp.filemanager.R

object GoogleLoginHelper {
    fun getGoogleSignInClient(context : Context) : GoogleSignInClient  {
        val signInOption = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE))
            .build()

        return GoogleSignIn.getClient(context, signInOption)
    }


    fun getLastSignedInAccount(context: Context) : GoogleSignInAccount?{
        return GoogleSignIn.getLastSignedInAccount(context)
    }



}