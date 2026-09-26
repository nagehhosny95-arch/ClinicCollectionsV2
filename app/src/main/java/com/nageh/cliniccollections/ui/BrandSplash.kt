package com.nageh.cliniccollections.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nageh.cliniccollections.R
import kotlinx.coroutines.delay

/**
 * Branded splash shown immediately after the system splash window hands over.
 *
 * The platform splash screen cannot display text, so this exists purely to show the
 * app name and tagline. It is deliberately brief: 600ms, roughly one fade, and it
 * does not block any loading work.
 */
@Composable
fun BrandSplash(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(600)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(EmeraldInk),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(132.dp).background(Color(0x14FFFFFF), RoundedCornerShape(36.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(88.dp)
                )
            }
            Box(Modifier.height(28.dp))
            Text(
                "Advance Medical",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Box(Modifier.height(6.dp))
            Text(
                "Smart Payment Tracking",
                style = MaterialTheme.typography.bodyMedium,
                color = Gold
            )
        }
    }
}
