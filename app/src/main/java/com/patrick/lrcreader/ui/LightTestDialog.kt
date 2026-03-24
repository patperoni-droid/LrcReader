package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.exo.R

@Composable
fun LightTestDialog(
    onRed: () -> Unit,
    onBlue: () -> Unit,
    onGreen: () -> Unit,
    onWhite: () -> Unit,
    onStrobe: () -> Unit,
    onBlackout: () -> Unit,
    onOff: () -> Unit,
    onQuickTest: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = Color(0xEE222222),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.light_test_title),
                color = Color.White
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LightTestButton(
                    label = stringResource(R.string.light_test_red),
                    onClick = onRed,
                    modifier = Modifier.weight(1f)
                )
                LightTestButton(
                    label = stringResource(R.string.light_test_blue),
                    onClick = onBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LightTestButton(
                    label = stringResource(R.string.light_test_green),
                    onClick = onGreen,
                    modifier = Modifier.weight(1f)
                )
                LightTestButton(
                    label = stringResource(R.string.light_test_white),
                    onClick = onWhite,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LightTestButton(
                    label = stringResource(R.string.light_test_strobe),
                    onClick = onStrobe,
                    modifier = Modifier.weight(1f)
                )
                LightTestButton(
                    label = stringResource(R.string.light_test_blackout),
                    onClick = onBlackout,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LightTestButton(
                    label = stringResource(R.string.light_test_off),
                    onClick = onOff,
                    modifier = Modifier.weight(1f)
                )
                LightTestButton(
                    label = stringResource(R.string.light_test_quick),
                    onClick = onQuickTest,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onClose) {
                    Text(text = stringResource(R.string.common_close))
                }
            }
        }
    }
}

@Composable
private fun LightTestButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(text = label)
    }
}
