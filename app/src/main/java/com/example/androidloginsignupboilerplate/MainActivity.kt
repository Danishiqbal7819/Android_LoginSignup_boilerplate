package com.example.androidloginsignupboilerplate

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.androidloginsignupboilerplate.ui.theme.AndroidLoginSignupBoilerplateTheme
import com.example.androidloginsignupboilerplate.view.HomeScreen
import com.example.androidloginsignupboilerplate.view.LoginScreen
import com.example.androidloginsignupboilerplate.view.SignupScreen
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val fireauth = FirebaseAuth.getInstance()
    private val TAG = "GoogleAuth"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidLoginSignupBoilerplateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    @Composable
    fun AppNavigation(modifier: Modifier = Modifier) {
        val navController = rememberNavController()
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(false) }

        val startRoute = if (fireauth.currentUser != null) "home" else "login"

        NavHost(
            navController = navController, 
            startDestination = startRoute,
            modifier = modifier
        ) {
            composable("login") {
                LoginScreen(
                    isLoading = isLoading,
                    onLoginClick = { email, password ->
                        isLoading = true
                        fireauth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                } else {
                                    Toast.makeText(context, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    },
                    onSignupClick = {
                        navController.navigate("signup")
                    }
                )
            }

            composable("signup") {
                SignupScreen(
                    isLoading = isLoading,
                    onSignupClick = { name, email, password ->
                        isLoading = true
                        fireauth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val user = fireauth.currentUser
                                    val profileUpdates = userProfileChangeRequest {
                                        displayName = name
                                    }

                                    user?.updateProfile(profileUpdates)
                                        ?.addOnCompleteListener { profileTask ->
                                            isLoading = false
                                            if (profileTask.isSuccessful) {
                                                navController.navigate("home") {
                                                    popUpTo("signup") { inclusive = true }
                                                }
                                            } else {
                                                Toast.makeText(context, "Profile Update Failed: ${profileTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Signup Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    },
                    onGoogleSignInClick = {
                        handleGoogleSignIn(
                            onLoading = { isLoading = it },
                            onSuccess = {
                                navController.navigate("home") {
                                    popUpTo("signup") { inclusive = true }
                                }
                                Toast.makeText(context, "Login Success!", Toast.LENGTH_SHORT).show()
                            },
                            onError = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    onLoginClick = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable("home") {
                HomeScreen(
                    onLogoutClick = {
                        fireauth.signOut()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
    }

    private fun handleGoogleSignIn(
        onLoading: (Boolean) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val credentialManager = CredentialManager.create(this)

        val webClientId =getString(R.string.default_web_client_id)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {

            onLoading(true)

            try {

                val result = credentialManager.getCredential(
                    context = this@MainActivity,
                    request = request
                )

                val credential = result.credential

                if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {

                    val googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(
                            credential.data
                        )

                    val firebaseCredential =
                        GoogleAuthProvider.getCredential(
                            googleIdTokenCredential.idToken,
                            null
                        )

                    fireauth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->

                            onLoading(false)

                            if (task.isSuccessful) {

                                Log.d(TAG, "Google Sign-In successful")

                                onSuccess()

                            } else {

                                Log.e(
                                    TAG,
                                    "Firebase Sign-In failed",
                                    task.exception
                                )

                                onError(
                                    task.exception?.message
                                        ?: "Firebase Sign-In failed"
                                )
                            }
                        }

                } else {

                    onLoading(false)

                    onError("Unexpected credential type")
                }

            } catch (e: GetCredentialException) {

                onLoading(false)

                Log.e(TAG, "Credential Manager Error", e)

                onError(
                    "Google Sign-In failed: ${e.message}"
                )

            } catch (e: Exception) {

                onLoading(false)

                Log.e(TAG, "Unknown Error", e)

                onError(
                    "An unexpected error occurred: ${e.message}"
                )
            }
        }
    }
}