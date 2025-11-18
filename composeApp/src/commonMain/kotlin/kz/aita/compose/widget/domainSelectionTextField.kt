package kz.aita.compose.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kz.aita.AppConfiguration
import kz.aita.compose.render.kamelConfig
import kz.aita.compose.wrapper.ImeWithAction
import kz.aita.core.StateHost
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.core.search

@Composable
fun AppConfiguration.domainSelectionTextField(
  modifier: Modifier = Modifier,
  valueInitial: String? = null,
  titleText: String,
  stateHost: StateHost,
  stateKey: String,
  placeholderText: String,
  titleIconButtonPath: String? = null,
  onTitleIconButtonClick: (() -> Unit)? = null,
  domains: List<SelectableDomain>,
  secondaryDomains: List<SelectableDomain>? = null,
  selectedInitial: String = try {
    domains.first().id
  } catch (_: Throwable) {
    ""
  },
  selectedSecondaryInitial: String? = secondaryDomains?.first()?.id,
  selectionEnabled: Boolean = true,
  selectionSecondaryEnabled: Boolean = true,
  displayFullDomain: Boolean = false,
  imeWithAction: ImeWithAction? = null,
  cornerRadius: Dp = stateValues.cornerRadius,
  keyboardType: KeyboardType = KeyboardType.Text,
  isFocusedInitial: Boolean = false,
  secondaryDomainsShowId: Boolean = true,
  secondaryDomainsShowName: Boolean = true,
  contentInvalidText: String? = null,
  onContentValidityCheck: ((String, String, String?) -> Boolean)? = null,
  onFilterValue: ((String, String, String?) -> Boolean)? = null,
  onValueChange: ((String, String, String?, () -> Unit) -> Unit)? = null
): DomainSelectionTextFieldContent {
  var selectedId by rememberSaveable {
    mutableStateOf(selectedInitial)
  }

  var selected by remember {
    mutableStateOf(
      domains.find { it.id.equals(selectedId, true) } ?: try {
        domains.first()
      } catch (thr: Throwable) {
        null
      }
    )
  }

  LaunchedEffect(selectedId) {
    domains.find { it.id.equals(selectedId, true) }?.run {
      selected = this
    }
  }

  LaunchedEffect(selectedInitial) {
    selectedId = selectedInitial.takeIf { it.isNotEmpty() } ?: try {
      domains.first().id
    } catch (thr: Throwable) {
      ""
    }
  }

  var selectedSecondaryId by rememberSaveable {
    mutableStateOf(selectedSecondaryInitial)
  }

  var selectedSecondary by remember {
    mutableStateOf(secondaryDomains?.find { it.id.equals(selectedSecondaryId, true) } ?: secondaryDomains?.first())
  }

  LaunchedEffect(selectedSecondaryId) {
    secondaryDomains?.find { it.id.equals(selectedSecondaryId, true) }?.run {
      selectedSecondary = this
    }
  }

  LaunchedEffect(selectedSecondaryInitial) {
    selectedSecondaryId = selectedSecondaryInitial
  }

  val isDomainSelectionDropdownExpandedState = remember {
    MutableTransitionState(false)
      .apply {
        targetState = false
      }
  }

  val isSecondaryDomainSelectionDropdownExpandedState = remember {
    MutableTransitionState(false)
      .apply {
        targetState = false
      }
  }

  var textFieldContent: GenericTextFieldContent? = null

  Column(modifier) {
    val titleTextPresent = titleText.isNotEmpty() && titleText.isNotBlank()

    if (titleTextPresent || titleIconButtonPath != null)
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (titleIconButtonPath != null)
          CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
            KamelImage(
              modifier = Modifier
                .padding(2.dp)
                .size(stateValues.iconSize)
                .clickable(
                  interactionSource = remember {
                    MutableInteractionSource()
                  },
                  indication = ripple(color = stateValues.TextColor, radius = cornerRadius),
                  onClick = onTitleIconButtonClick ?: {}
                ),
              resource = {
                asyncPainterResource(
                  data = Url(getFullDrawableResourceUrl(titleIconButtonPath))
                )
              },
              contentDescription = titleText,
              onFailure = {
                KamelImage(
                  modifier = Modifier
                    .padding(2.dp)
                    .size(stateValues.iconSize)
                    .clickable(
                      interactionSource = remember {
                        MutableInteractionSource()
                      },
                      indication = ripple(color = stateValues.TextColor, radius = cornerRadius),
                      onClick = onTitleIconButtonClick ?: {}
                    ),
                  resource = {
                    asyncPainterResource(
                      data = Url(getFullDrawableResourceUrl(titleIconButtonPath))
                    )
                  },
                  contentDescription = titleText,
                  onFailure = {

                  }
                )
              }
            )
          }

        if (titleTextPresent)
          Text(
            modifier = Modifier
              .padding(bottom = 4.dp),
            text = titleText,
            style = TextStyle(
              color = stateValues.TextColor,
              fontSize = stateValues.accentTextSize,
              fontWeight = FontWeight.Bold
            )
          )
      }

    var isFocused by rememberSaveable {
      mutableStateOf(isFocusedInitial)
    }

    Column(
      modifier = Modifier
        .clip(RoundedCornerShape(cornerRadius))
        .border(
          width = if (isFocused) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
          color = if (isFocused) stateValues.AccentColor else stateValues.PlaceholderTextColor,
          shape = RoundedCornerShape(cornerRadius)
        )
    ) {
      if (selectionSecondaryEnabled && !secondaryDomains.isNullOrEmpty()) {
        AnimatedVisibility(
          modifier = Modifier
            .fillMaxWidth(),
          visibleState = isSecondaryDomainSelectionDropdownExpandedState,
          enter = expandVertically(),
          exit = shrinkVertically()
        ) {
          Column {
            var searchTextFieldContent: GenericTextFieldContent? = null

            searchTextFieldContent = searchTextField(
              modifier = Modifier
                .fillMaxWidth(),
              stateHost = stateHost,
              stateKey = stateKey,
              focusedBorderWidth = 0.dp,
              unfocusedBorderWidth = 0.dp,
              focusedBorderColor = Color.Transparent,
              unfocusedBorderColor = Color.Transparent
            )

            Spacer(
              modifier = Modifier
                .fillMaxWidth()
                .background(stateValues.PlaceholderTextColor)
                .height(stateValues.unfocusedBorderWidth)
            )

            val items = secondaryDomains
              .takeIf { it.isNotEmpty() && searchTextFieldContent.value.text.isNotEmpty() }
              ?.search<SelectableDomain>(query = searchTextFieldContent.value.text)
              ?.first ?: secondaryDomains


            if (items.isEmpty()) {
              MessageText(
                modifier = Modifier
                  .fillMaxWidth(),
                text = stateValues.stringNoMatches
              )
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = stateValues.screenHeight / 4)
              ) {

                items(items) { domain ->
                  selectableDomainWidget(
                    modifier = Modifier
                      .fillParentMaxWidth(),
                    domain = domain,
                    showId = secondaryDomainsShowId,
                    showName = secondaryDomainsShowName
                  ) {
                    selectedSecondaryId = domain.id

                    isSecondaryDomainSelectionDropdownExpandedState.targetState =
                      !isSecondaryDomainSelectionDropdownExpandedState.targetState
                  }
                }
              }
            }

            Spacer(
              modifier = Modifier
                .fillMaxWidth()
                .background(stateValues.PlaceholderTextColor)
                .height(stateValues.unfocusedBorderWidth)
            )
          }
        }
      }

      textFieldContent = genericTextField(
        modifier = Modifier
          .fillMaxWidth(),
        valueInitial = valueInitial,
        titleText = "",
        stateHost = stateHost,
        stateKey = stateKey,
        placeholderText = placeholderText,
        isFocusedInitial = isFocusedInitial,
        leadingIcon = selectedSecondary?.run {
          {
            selectableDomainWidget(
              domain = this,
              state = isSecondaryDomainSelectionDropdownExpandedState,
              showExpansion = true,
              showId = secondaryDomainsShowId,
              showName = false,
              onClick = selectionSecondaryEnabled.takeIf { it }?.run {
                {
                  isSecondaryDomainSelectionDropdownExpandedState.targetState =
                    !isSecondaryDomainSelectionDropdownExpandedState.targetState
                }
              }
            )
          }
        },
        cornerShape = RectangleShape,
        keyboardType = keyboardType,
        imeWithAction = imeWithAction,
        focusedBorderWidth = 0.dp,
        unfocusedBorderWidth = 0.dp,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        contentInvalidText = contentInvalidText,
        onContentValidityCheck = onContentValidityCheck?.run {
          {
            invoke(it, selectedId, selectedSecondaryId)
          }
        },
        onFilterValue = onFilterValue?.run {
            {
              println(selectedId)
              invoke(it, selectedId, selectedSecondaryId)
            }
        },
        onValueChange = onValueChange?.run {
          { value, action ->
            invoke(value, selectedId, selectedSecondaryId, action)
          }
        }
      )

      LaunchedEffect(textFieldContent.isFocused) {
        isFocused = textFieldContent.isFocused
      }

      Spacer(
        modifier = Modifier
          .fillMaxWidth()
          .background(stateValues.PlaceholderTextColor)
          .height(stateValues.unfocusedBorderWidth)
      )

      if (displayFullDomain && domains.isNotEmpty() && selected != null) {
        selectableDomainWidget(
          modifier = Modifier
            .fillMaxWidth(),
          domain = selected!!,
          showName = secondaryDomainsShowName,
          state = isDomainSelectionDropdownExpandedState,
          showExpansion = true
        ) {
          isDomainSelectionDropdownExpandedState.targetState =
            !isDomainSelectionDropdownExpandedState.targetState
        }
      }

      if (selectionEnabled && domains.isNotEmpty()) {
        AnimatedVisibility(
          modifier = Modifier
            .fillMaxWidth(),
          visibleState = isDomainSelectionDropdownExpandedState,
          enter = expandVertically(),
          exit = shrinkVertically()
        ) {
          Column {
            var searchTextFieldContent: GenericTextFieldContent? = null

            Spacer(
              modifier = Modifier
                .fillMaxWidth()
                .background(stateValues.PlaceholderTextColor)
                .height(stateValues.unfocusedBorderWidth)
            )

            searchTextFieldContent = searchTextField(
              modifier = Modifier
                .fillMaxWidth(),
              stateHost = stateHost,
              stateKey = stateKey,
              focusedBorderWidth = 0.dp,
              unfocusedBorderWidth = 0.dp,
              focusedBorderColor = Color.Transparent,
              unfocusedBorderColor = Color.Transparent
            )

            Spacer(
              modifier = Modifier
                .fillMaxWidth()
                .background(stateValues.PlaceholderTextColor)
                .height(stateValues.unfocusedBorderWidth)
            )

            val items = domains
              .takeIf { it.isNotEmpty() && searchTextFieldContent.value.text.isNotEmpty() }
              ?.search<SelectableDomain>(query = searchTextFieldContent.value.text)
              ?.first ?: domains

            if (items.isEmpty()) {
              MessageText(
                modifier = Modifier
                  .fillMaxWidth(),
                text = stateValues.stringNoMatches
              )
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = stateValues.screenHeight / 4)
              ) {

                items(items) { domain ->
                  selectableDomainWidget(
                    modifier = Modifier
                      .fillParentMaxWidth(),
                    domain = domain,
                    showName = secondaryDomainsShowName
                  ) {
                    selectedId = domain.id

                    isDomainSelectionDropdownExpandedState.targetState =
                      !isDomainSelectionDropdownExpandedState.targetState
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  return DomainSelectionTextFieldContent(
    value = textFieldContent!!.value,
    isFocused = textFieldContent.isFocused,
    selectedId = selectedId,
    selectedSecondaryId = selectedSecondaryId,
    isContentValid = textFieldContent.isContentValid,
    onContentValidityCheck = textFieldContent.onContentValidityCheck
  )
}

class DomainSelectionTextFieldContent(
  var value: TextFieldValue,
  var selectedId: String,
  var selectedSecondaryId: String?,
  var isFocused: Boolean,
  var isContentValid: Boolean,
  val onContentValidityCheck: ((String) -> Boolean)? = null
) {

  fun checkContentValidity() {
    isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
  }
}
