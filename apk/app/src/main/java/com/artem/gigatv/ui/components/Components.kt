package com.artem.gigatv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.artem.gigatv.ui.theme.AccentBlue
import com.artem.gigatv.ui.theme.CardDark
import com.artem.gigatv.ui.theme.TextMuted

/**
 * Компоненты Compose for TV реагируют только на DPAD-события (пульт).
 * Этот модификатор добавляет обработку обычных касаний, чтобы интерфейс
 * работал и на сенсорных экранах (телефон, планшет, ТВ с тачскрином).
 */
fun Modifier.tapClick(onClick: () -> Unit): Modifier =
    pointerInput(onClick) {
        detectTapGestures(onTap = { onClick() })
    }

/** Кнопка TV Material, дополнительно реагирующая на касания. */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Долгое нажатие на пульте — например, убрать запрос из истории поиска. */
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        modifier = if (enabled) modifier.tapClick(onClick) else modifier,
        content = content
    )
}

@Composable
fun MovieCard(
    title: String,
    posterUrl: String?,
    subtitle: String? = null,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    /** Долгое нажатие на пульте — например, убрать из «Продолжить просмотр». */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var posterLoaded by remember(posterUrl) { mutableStateOf(false) }
    Card(
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        onClick = onClick,
        onLongClick = { onLongClick?.invoke() },
        modifier = modifier.width(150.dp).tapClick(onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(210.dp)
                    .background(CardDark)
            ) {
                if (!posterLoaded) {
                    Text(
                        text = title,
                        textAlign = TextAlign.Center,
                        color = TextMuted,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).padding(12.dp)
                    )
                }
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { posterLoaded = true },
                        onError = { posterLoaded = false }
                    )
                }
                if (progress != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color(0xB3000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(AccentBlue)
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = subtitle ?: " ",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            // BasicTextField забирает себе все DPAD-события, из-за чего фокус
            // запирается в поле и всё, что ниже, становится недостижимо с пульта.
            // Вертикальные стрелки перехватываем до него и уводим фокус.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (focused) AccentBlue else Color(0xFF3A4150),
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color(0xFF161A21), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = TextMuted)
                }
                inner()
            }
        }
    )
}
