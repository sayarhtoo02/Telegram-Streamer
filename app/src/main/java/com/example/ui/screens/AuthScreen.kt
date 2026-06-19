package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBackground
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.NetflixRed
import com.example.viewmodel.MainViewModel
import org.drinkless.tdlib.TdApi

@Composable
fun AuthScreen(
    viewModel: MainViewModel,
    onAuthSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val scrollState = rememberScrollState()

    var apiId by remember { mutableStateOf("123456") }
    var apiHash by remember { mutableStateOf("abcdef0123456789abcdef0123456789") }
    var phoneNumber by remember { mutableStateOf("+959") }
    var verificationCode by remember { mutableStateOf("") }
    var password2FA by remember { mutableStateOf("") }

    if (authState is TdApi.AuthorizationStateReady) {
        onAuthSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NetflixRed.copy(alpha = 0.15f),
                        DarkBackground
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TG STREAMER",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = NetflixRed,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your Server-Less Movie Library",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_card"),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (authState) {
                        is TdApi.AuthorizationStateWaitTdlibParameters, is TdApi.AuthorizationStateClosed -> {
                            Text(
                                text = "Telegram API Keys",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Enter api_id and api_hash from my.telegram.org. You can also specify these configurations dynamically without public exposures.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = apiId,
                                onValueChange = { apiId = it },
                                label = { Text("API ID") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NetflixRed,
                                    focusedLabelColor = NetflixRed,
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_id_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = apiHash,
                                onValueChange = { apiHash = it },
                                label = { Text("API Hash") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NetflixRed,
                                    focusedLabelColor = NetflixRed,
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_hash_input")
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    val idInt = apiId.toIntOrNull() ?: 123456
                                    viewModel.initializeTdLibParameters(idInt, apiHash)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("init_button")
                            ) {
                                Text("Connect to Telegram", fontWeight = FontWeight.Bold)
                            }
                        }

                        is TdApi.AuthorizationStateWaitPhoneNumber -> {
                            Text(
                                text = "Verify Your Account",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Enter your personal Telegram phone number including your country code (e.g. +959 for Myanmar, +1 for US).",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Phone Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NetflixRed,
                                    focusedLabelColor = NetflixRed,
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_number_input")
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { viewModel.sendPhoneNumber(phoneNumber) },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("send_phone_button")
                            ) {
                                Text("Request OTP Code", fontWeight = FontWeight.Bold)
                            }
                        }

                        is TdApi.AuthorizationStateWaitCode -> {
                            Text(
                                text = "Enter OTP Code",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "A login verification code was sent directly via your active Telegram app. Enter it below.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = verificationCode,
                                onValueChange = { verificationCode = it },
                                label = { Text("Verification Code") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NetflixRed,
                                    focusedLabelColor = NetflixRed,
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_code_input")
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { viewModel.sendCode(verificationCode) },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_otp_button")
                            ) {
                                Text("Verify & Login", fontWeight = FontWeight.Bold)
                            }
                        }

                        is TdApi.AuthorizationStateWaitPassword -> {
                            Text(
                                text = "Two-Factor Verification",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Two-Step 2FA security password is enabled on your Telegram account. Enter your security vault password below.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = password2FA,
                                onValueChange = { password2FA = it },
                                label = { Text("Cloud Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NetflixRed,
                                    focusedLabelColor = NetflixRed,
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("2fa_password_input")
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { viewModel.sendPassword(password2FA) },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_2fa_button")
                            ) {
                                Text("Submit Password", fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> {
                            Text(
                                text = "Establishing secure MTProto connection. Please wait...",
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Note: Operating via official Telegram MTProto JNI bindings securely directly to your device with local sandboxed database keys.",
                fontSize = 11.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
