// THIS IS CommonMainCompose.kt - in commonMain shared module of kmp compose app
@file:OptIn(ExperimentalTime::class)
package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.kamel.core.config.*
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.svgDecoder
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kz.aita.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.text.equals
import kotlin.time.ExperimentalTime

fun getTransformedTextWithSelectionFocusTextColor(
  textFieldValue: TextFieldValue,
  selectionFocusTextColor: Color
): TransformedText {
  return AnnotatedString.Builder()
    .apply {
      for (i in textFieldValue.text.indices) {
        if (i in textFieldValue.selection.min until textFieldValue.selection.max)
          withStyle(SpanStyle(color = selectionFocusTextColor)) { append(textFieldValue.text[i]) }
        else
          append(textFieldValue.text[i])
      }
    }
    .toAnnotatedString()
    .run {
      TransformedText(
        this,
        offsetMapping = OffsetMapping.Identity
      )
    }
}

fun getPasswordTransformedTextWithSelectionFocusTextColor(
  textFieldValue: TextFieldValue,
  selectionFocusTextColor: Color
): TransformedText {
  return AnnotatedString.Builder()
    .apply {
      val maskChar = '•'
      for (i in textFieldValue.text.indices) {
        if (i in textFieldValue.selection.min until textFieldValue.selection.max)
          withStyle(SpanStyle(color = selectionFocusTextColor)) { append (maskChar) }
        else
          append(maskChar)
      }
    }
    .toAnnotatedString()
    .run {
      TransformedText(
        this,
        offsetMapping = OffsetMapping.Identity
      )
    }
}

suspend fun loadResourceStrings(): List<LocalizedStringGroupDataModel> {
  return jsonBase.decodeFromString<ResponseDataModel<List<LocalizedStringGroupDataModel>>>(
    Res.readBytes("files/assets/values/strings.json").decodeToString()
  ).payload!!
}

suspend fun loadResourceDimensions(): List<StylizedDimensionGroupDataModel> {
  return jsonBase.decodeFromString<ResponseDataModel<List<StylizedDimensionGroupDataModel>>>(
    Res.readBytes("files/assets/values/dimensions.json").decodeToString()
  ).payload!!
}

suspend fun loadResourceColors(): List<StylizedColorGroupDataModel> {
  return jsonBase.decodeFromString<ResponseDataModel<List<StylizedColorGroupDataModel>>>(
    Res.readBytes("files/assets/values/colors.json").decodeToString()
  ).payload!!
}


suspend fun loadResourceDrawablePaths(): List<StylizedDrawablePathsGroupDataModel> {
  return jsonBase.decodeFromString<ResponseDataModel<List<StylizedDrawablePathsGroupDataModel>>>(
    Res.readBytes("files/assets/drawable/drawables.json").decodeToString()
  ).payload!!
}

@Composable
fun AppConfiguration.UserAuthSignUpScreen(
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (stateValues.isNarrowScreen)
      Spacer(
        modifier = Modifier
          .height(stateValues.screenHeight / 6)
      )

    Column(
      modifier = Modifier
        .width(stateValues.boundWidgetWidth),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      if (stateValues.isNarrowScreen) {
        val drawableResAITALogo by stateValues.drawableResAITALogo.collectAsState()

        LargeIconWithTitleWidget(
          imageUrl = stateValues.drawablePathAITALogo,
          imageRes = drawableResAITALogo,
          title = stateValues.stringSignUp,
        )
      }

      if (!stateValues.isNarrowScreen)
        Text(
          text = stateValues.stringSignUp,
          style = TextStyle(
            color = stateValues.TextColor,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold
          )
        )

      val outerSpace = 16.dp
      val innerSpace = 8.dp

      Spacer(modifier = Modifier.height(outerSpace))

      val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER,
        lockedId = stateValues.globalAppConfiguration.countries.find { it.locale == "kz" }?.phoneNumberCode
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val emailTextFieldContent = emailTextField(
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = NavigationScreenModel.KEY_STATE_EMAIL,
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val firstNameTextFieldContent = genericTextField(
        titleText = stateValues.stringFirstName,
        placeholderText = stateValues.stringEnterFirstName,
        leadingIconPath = stateValues.drawablePathIconPerson,
        contentInvalidText = stateValues.stringFirstNameCannotBeEmptyOrJustWhitespaces,
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = NavigationScreenModel.KEY_STATE_FIRST_NAME,
        onContentValidityCheck = {
          it.checkAsPersonName()
        },
        onFilterValue = {
          it.filterAsPersonName()
        }
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val lastNameTextFieldContent = genericTextField(
        titleText = stateValues.stringLastName,
        placeholderText = stateValues.stringEnterLastName,
        leadingIconPath = stateValues.drawablePathIconPerson,
        contentInvalidText = stateValues.stringLastNameCannotBeEmptyOrJustWhitespaces,
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = NavigationScreenModel.KEY_STATE_LAST_NAME,
        onContentValidityCheck = {
          it.checkAsPersonName()
        },
        onFilterValue = {
          it.filterAsPersonName()
        }
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val (passwordTextFieldContent, repeatedPasswordTextFieldContent) = repeatedPasswordTextFieldGroup(
        stateHost = NavigationScreenModel.UserAuth.SignUp,
        stateKey = "new_password",
        repeatedStateKey = "repeated_password",
      )

      Spacer(modifier = Modifier.height(outerSpace))

      actionButton(
        text = stateValues.stringSignUp,
        enabled = stateValues.latestNotification?.message?.equals(stateValues.stringSignUp) != true,
        icon = if (stateValues.latestNotification?.message?.equals(stateValues.stringSignUp) == true) {
          {
            CircularProgressIndicator(
              color = stateValues.AccentTextColor,
              modifier = Modifier
                .padding(start = 20.dp)
                .size(20.dp)
            )
          }
        } else null
      ) {
        phoneNumberTextFieldContent.checkContentValidity()
        emailTextFieldContent.checkContentValidity()

        firstNameTextFieldContent.checkContentValidity()
        lastNameTextFieldContent.checkContentValidity()

        passwordTextFieldContent.checkContentValidity()
        repeatedPasswordTextFieldContent.checkContentValidity()

        if (
          phoneNumberTextFieldContent.isContentValid
          && emailTextFieldContent.isContentValid
          && firstNameTextFieldContent.isContentValid
          && lastNameTextFieldContent.isContentValid
          && passwordTextFieldContent.isContentValid
          && repeatedPasswordTextFieldContent.isContentValid
        )
          signUpUser (
            UserAuthSignUpDataModel(
              phoneNumber = stateValues.globalAppConfiguration.countries.run {
                find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
              }.phoneNumberCode + phoneNumberTextFieldContent.value.text.trim(),
              email = emailTextFieldContent.value.text.trim(),
              firstName = firstNameTextFieldContent.value.text.trim(),
              lastName = lastNameTextFieldContent.value.text.trim(),
              countryLocale = stateValues.globalAppConfiguration.countries.find { it.phoneNumberCode == phoneNumberTextFieldContent.selectedSecondaryId!!.substringAfter("+") }!!.locale,
              password = passwordTextFieldContent.value.text
            )
          )

      }

      if (stateValues.isNarrowScreen) {
        Spacer(modifier = Modifier.height(2.dp))

        actionButton(
          text = stateValues.stringCancel,
          enabledColor = stateValues.DisabledColor
        ) {
          coroutineScope.launch {
            Navigation.UserAuth.popLeft()
          }
        }
      }

      Spacer(
        modifier = Modifier
          .height(stateValues.screenHeight / 10)
      )
    }
  }
}

@Composable
fun AppConfiguration.UserAuthScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      item {
        if (stateValues.isNarrowScreen) {
          AnimatedContent(
            targetState = stateValues.navigationScreensUserAuthLeft.last()
          ) { model ->
            when (model) {
              is NavigationScreenModel.UserAuth.LogIn -> {
                UserAuthLogInScreen()
              }

              else -> {
                UserAuthSignUpScreen()
              }
            }
          }
        } else {
          val drawableResAITALogo by stateValues.drawableResAITALogo.collectAsState()

          LargeIconWithTitleWidget(
            modifier = Modifier
              .width(stateValues.boundWidgetWidth)
              .aspectRatio(1f, matchHeightConstraintsFirst = true),
            imageUrl = stateValues.drawablePathAITALogo,
            imageRes = drawableResAITALogo,
          )

          Row(
            modifier = Modifier
              .weight(1f)
          ) {
            AnimatedContent(
              modifier = Modifier
                .weight(1f),
              targetState = stateValues.navigationScreensUserAuthLeft.last()
            ) { model ->
              when (model) {
                is NavigationScreenModel.UserAuth.LogIn -> {
                  UserAuthLogInScreen()
                }

                else -> {
                  UserAuthSignUpScreen()
                }
              }
            }

            AnimatedContent(
              modifier = Modifier
                .weight(1f),
              targetState = stateValues.navigationScreensUserAuthRight.last()
            ) { model ->
              when (model) {
                is NavigationScreenModel.UserAuth.SignUp -> {
                  UserAuthSignUpScreen()
                }
                else -> { }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AppConfiguration.UserAuthLogInScreen() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (stateValues.isNarrowScreen)
      Spacer(
        modifier = Modifier
          .height(stateValues.screenHeight / 6)
      )

    Column(
      modifier = Modifier
        .width(stateValues.boundWidgetWidth),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      if (stateValues.isNarrowScreen) {
        val drawableResAITALogo by stateValues.drawableResAITALogo.collectAsState()

        LargeIconWithTitleWidget(
          imageUrl = stateValues.drawablePathAITALogo,
          imageRes = drawableResAITALogo,
          title = stateValues.stringLogIn
        )
      }

      if (!stateValues.isNarrowScreen)
        Text(
          text = stateValues.stringLogIn,
          style = TextStyle(
            color = stateValues.TextColor,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold
          )
        )

      val outerSpace = 16.dp
      val innerSpace = 8.dp

      Spacer(modifier = Modifier.height(outerSpace))

      val loginMethodTabRowContent = tabRowWidget(
        modifier = Modifier,
        tabs = listOf(
          TabContent("0", stateValues.stringPhoneNumber),
          TabContent("1", stateValues.stringEmail)
        )
      )

      Spacer(modifier = Modifier.height(innerSpace))

      val loginTextFieldContent = when (loginMethodTabRowContent.id) {
        "0" -> {
          countrySelectionPhoneNumberTextField(
            imeWithAction = ImeWithAction(ime = ImeAction.Next),
            stateHost = NavigationScreenModel.UserAuth.LogIn,
            stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER,
            lockedId = stateValues.globalAppConfiguration.countries.find { it.locale == "kz" }?.phoneNumberCode
          )
        }

        else -> {
          emailTextField(
            stateHost = NavigationScreenModel.UserAuth.LogIn,
            stateKey = NavigationScreenModel.KEY_STATE_EMAIL,
          )
        }
      }

      Spacer(modifier = Modifier.height(innerSpace))

      var goAction: (() -> Unit)? = null

      val passwordTextFieldContent = passwordTextField(
        imeWithAction = ImeWithAction(ImeAction.Go) {
          goAction?.invoke()

        },
        stateHost = NavigationScreenModel.UserAuth.LogIn,
        stateKey = "password",
      )

      Spacer(modifier = Modifier.height(outerSpace))

      goAction = {
        softKeyboardController?.hide()
        passwordTextFieldContent.checkContentValidity()

        val loginEmail = loginTextFieldContent !is DomainSelectionTextFieldContent

        if (loginEmail) {
          (loginTextFieldContent as GenericTextFieldContent).run {
            checkContentValidity()

            if (isContentValid && passwordTextFieldContent.isContentValid)
              logInUser(
                UserAuthLogInDataModel(
                  login = this.value.text,
                  password = passwordTextFieldContent.value.text
                )
              )
          }
        } else {
          loginTextFieldContent.run {
            checkContentValidity()

            if (isContentValid && passwordTextFieldContent.isContentValid)
              logInUser(
                UserAuthLogInDataModel(
                  login = stateValues.globalAppConfiguration.countries.run {
                    find { it.locale.equals(loginTextFieldContent.selectedId, true) } ?: first()
                  }.phoneNumberCode + loginTextFieldContent.value.text.trim(),
                  password = passwordTextFieldContent.value.text
                )
              )
          }
        }
      }

      actionButton(
        text = stateValues.stringLogIn,
        enabled = stateValues.latestNotification?.message?.equals(stateValues.stringLoggingIn) != true,
        icon = if (stateValues.latestNotification?.message?.equals(stateValues.stringLoggingIn) == true) {
          {
            CircularProgressIndicator(
              color = stateValues.AccentTextColor,
              modifier = Modifier
                .padding(start = 20.dp)
                .size(20.dp)
            )
          }
        } else null,
        onClick = goAction
      )

      if (stateValues.isNarrowScreen) {
        Spacer(modifier = Modifier.height(2.dp))

        actionButton(
          text = stateValues.stringSignUp,
          enabled = stateValues.latestNotification == null
        ) {
          coroutineScope.launch {
            Navigation.UserAuth.goLeft(NavigationScreenModel.UserAuth.SignUp)
          }
        }
      }

    }

    Spacer(
      modifier = Modifier
        .height(stateValues.screenHeight / 10)
    )
  }
}

private data class TransactionUiContext(
  val transactionTypeIndex: Int,
  val clientId: Int,
  val stateHost: StateHost
)

@Composable
private fun AppConfiguration.rememberTransactionContext(): TransactionUiContext {
  val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
    is NavigationScreenModel.Transaction.MainSale -> 0
    is NavigationScreenModel.Transaction.MainReturn -> 1
    else -> 2
  }

  val clientId = when (transactionTypeIndex) {
    0 -> stateValues.navigationTransactionSaleClientId
    1 -> stateValues.navigationTransactionReturnClientId
    else -> stateValues.navigationTransactionSupplyClientId
  }

  val stateHost = when (transactionTypeIndex) {
    0 -> NavigationScreenModel.Transaction.MainSale
    1 -> NavigationScreenModel.Transaction.MainReturn
    else -> NavigationScreenModel.Transaction.MainSupply
  }

  return TransactionUiContext(
    transactionTypeIndex = transactionTypeIndex,
    clientId = clientId,
    stateHost = stateHost
  )
}

@Composable
fun AppConfiguration.TransactionSelectionScreen() {
  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    val context = rememberTransactionContext()

    val canGoBack = when (context.transactionTypeIndex) {
      0 -> !Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, context.clientId)
      1 -> !Navigation.TransactionReturn.isVeryFirstScreen(stateValues.isNarrowScreen, context.clientId)
      else -> !Navigation.TransactionSupply.isVeryFirstScreen(stateValues.isNarrowScreen, context.clientId)
    }

    ScreenAppBarWidget(
      title = stateValues.stringSelect,
      onBack = if (canGoBack) {
        {
          coroutineScope.launch {
            when (context.transactionTypeIndex) {
              0 -> Navigation.TransactionSale.pop()
              1 -> Navigation.TransactionReturn.pop()
              else -> Navigation.TransactionSupply.pop()
            }
          }
        }
      } else null
    )

    val goodsInCart by getCartState(
      context.transactionTypeIndex,
      context.clientId
    ).collectAsState()

    var searchTextFieldFocused by rememberSaveable {
      mutableStateOf(true)
    }

    val searchTextFieldContent = searchTextField(
      stateHost = context.stateHost,
      stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
      isFocusedInitial = searchTextFieldFocused,
      forceRefocus = true,
      modifier = Modifier.padding(stateValues.marginTextField),
      barcodeCamScanner = true
    )

    val scopeRowContent = tabRowWidget(
      modifier = Modifier.padding(horizontal = stateValues.marginTextField),
      tabs = listOf(
        TabContent("0", stateValues.stringAll),
        TabContent("1", stateValues.stringQuick)
      )
    )

    val addToCartAction: (GoodsItemDataModel) -> Unit = { goodsItem ->
      addGoodsItemToTransactionCart(
        goodsItem = goodsItem,
        transactionTypeIndex = context.transactionTypeIndex,
        clientId = context.clientId,
        configuration = stateValues.globalAppConfiguration,
        currentCart = goodsInCart
      )
    }

    StockWarehouseScreenContent(
      modifier = Modifier.weight(1f),
      searchQuery = searchTextFieldContent.value.text,
      onExactSearchHit = addToCartAction,
      disableIfOutOfStock = context.transactionTypeIndex == 0,
      showStockType = false,
      onFilter = when (scopeRowContent.id) {
        "1" -> {
          { it.isQuickItem }
        }

        else -> null
      },
      onClick = addToCartAction
    )
  }
}

//@Composable
//fun AppConfiguration.TransactionSelectionScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainSale -> {
//        0
//      }
//
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        1
//      }
//
//      else -> {
//        2
//      }
//    }
//
//    val clientId = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        stateValues.navigationTransactionReturnClientId
//      }
//
//      is NavigationScreenModel.Transaction.MainSupply -> {
//        stateValues.navigationTransactionSupplyClientId
//      }
//
//      else -> {
//        stateValues.navigationTransactionSaleClientId
//      }
//    }
//
//    ScreenAppBarWidget(
//      title = stateValues.stringSelect,
//      onBack = if (!Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)) {
//        {
//          coroutineScope.launch {
//            Navigation.Menu.pop(stateValues.isNarrowScreen)
//          }
//        }
//      } else null
//    )
//
//    var searchTextFieldFocused by rememberSaveable {
//      mutableStateOf(true)
//    }
//
//    val searchTextFieldContent =
//      searchTextField(
//        stateHost = when (transactionTypeIndex) {
//          0 -> NavigationScreenModel.Transaction.MainSale
//          1 -> NavigationScreenModel.Transaction.MainReturn
//          else -> NavigationScreenModel.Transaction.MainSupply
//        },
//        stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
//        isFocusedInitial = searchTextFieldFocused,
//        forceRefocus = true,
//        modifier = Modifier
//          .padding(stateValues.marginTextField),
//        barcodeCamScanner = true
//      )
//
//    val scopeRowContent = tabRowWidget(
//      modifier = Modifier
//        .padding(horizontal = stateValues.marginTextField),
//      tabs = listOf(
//        TabContent("0", stateValues.stringAll),
//        TabContent("1", stateValues.stringQuick)
//      )
//    )
//
//    val addToCartAction: (GoodsItemDataModel) -> Unit = {
//      upsertCart(
//        id = it.id,
//        transactionTypeIndex,
//        clientId,
//        QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
//      )
//    }
//
//    when (scopeRowContent.id) {
//      "0" -> {
//        StockWarehouseScreenContent(
//          modifier = Modifier
//            .weight(1f),
//          searchTextFieldContent.value.text,
//          onExactSearchHit = addToCartAction,
//          disableIfOutOfStock = true,
//          onClick = addToCartAction,
//          showStockType = false
//        )
//      }
//
//      "1" -> {
//        StockWarehouseScreenContent(
//          modifier = Modifier
//            .weight(1f),
//          searchTextFieldContent.value.text,
//          onExactSearchHit = addToCartAction,
//          disableIfOutOfStock = true,
//          showStockType = false,
//          onFilter = {
//            it.isQuickItem
//          },
//          onClick = addToCartAction
//        )
//      }
//    }
//  }
//}

@Composable
fun AppConfiguration.TransactionScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (stateValues.activeStoreId == null) {
      Column(
        modifier = Modifier
          .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        MessageText(
          text = stateValues.stringNoActiveStore,
          textSize = stateValues.titleTextSize
        )

        actionButton(
          text = stateValues.stringSelectInMenu,
          fillMaxWidthIfTextPresent = false
        ) {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.Stores)
            Navigation.goMain(NavigationScreenModel.Menu.Main)
          }
        }
      }
    } else {
      val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
        is NavigationScreenModel.Transaction.MainSale -> {
          0
        }
        is NavigationScreenModel.Transaction.MainReturn -> {
          1
        }
        else -> {
          2
        }
      }

      val clientId = when(transactionTypeIndex) {
        0 -> {
          stateValues.navigationTransactionSaleClientId
        }
        1 -> {
          stateValues.navigationTransactionReturnClientId
        }
        else -> {
          stateValues.navigationTransactionSupplyClientId
        }
      }

      val goodsInCart by getCartState(transactionTypeIndex, clientId).collectAsState()

//      LaunchedEffect(goodsInCart) {
//        if (goodsInCart.isEmpty())
//          coroutineScope.launch {
//            when (transactionTypeIndex) {
//              0 -> Navigation.TransactionSale.clear()
//              1 -> Navigation.TransactionReturn.clear()
//              2 -> Navigation.TransactionSupply.clear()
//            }
//          }
//      }

      val latestReceiptSnapshot by latestTransactionReceiptSnapshotState.collectAsState()

      LaunchedEffect(goodsInCart, latestReceiptSnapshot) {
        val currentScreens = Navigation
          .getCurrentTransactionScreens(transactionTypeIndex, clientId, stateValues.isNarrowScreen)
          .value

        val showingReceipt = currentScreens.lastOrNull() is NavigationScreenModel.Transaction.ReceiptPreview

        val receiptBelongsHere =
          latestReceiptSnapshot?.paymentDraft?.transactionTypeIndex == transactionTypeIndex &&
              latestReceiptSnapshot?.paymentDraft?.clientId == clientId

        if (goodsInCart.isEmpty() && !(showingReceipt && receiptBelongsHere)) {
          coroutineScope.launch {
            when (transactionTypeIndex) {
              0 -> Navigation.TransactionSale.clear()
              1 -> Navigation.TransactionReturn.clear()
              2 -> Navigation.TransactionSupply.clear()
            }
          }
        }
      }

      val navigationScreensLeft =
        when (stateValues.navigationScreensMain.last()) {
          is NavigationScreenModel.Transaction.MainSale -> {
            when (stateValues.navigationTransactionSaleClientId) {
              0 -> stateValues.navigationScreensTransactionSaleLeftClient1
              1 -> stateValues.navigationScreensTransactionSaleLeftClient2
              2 -> stateValues.navigationScreensTransactionSaleLeftClient3
              3 -> stateValues.navigationScreensTransactionSaleLeftClient4
              else -> stateValues.navigationScreensTransactionSaleLeftClient5
            }
          }

          is NavigationScreenModel.Transaction.MainReturn -> {
            when (stateValues.navigationTransactionReturnClientId) {
              0 -> stateValues.navigationScreensTransactionReturnLeftClient1
              1 -> stateValues.navigationScreensTransactionReturnLeftClient2
              2 -> stateValues.navigationScreensTransactionReturnLeftClient3
              3 -> stateValues.navigationScreensTransactionReturnLeftClient4
              else -> stateValues.navigationScreensTransactionReturnLeftClient5
            }
          }

          is NavigationScreenModel.Transaction.MainSupply -> {
            when (stateValues.navigationTransactionSupplyClientId) {
              0 -> stateValues.navigationScreensTransactionSupplyLeftClient1
              1 -> stateValues.navigationScreensTransactionSupplyLeftClient2
              2 -> stateValues.navigationScreensTransactionSupplyLeftClient3
              3 -> stateValues.navigationScreensTransactionSupplyLeftClient4
              else -> stateValues.navigationScreensTransactionSupplyLeftClient5
            }
          }

          else -> {
            emptyList()
          }
        }

      val navigationScreensRight =
        when (stateValues.navigationScreensMain.last()) {
          is NavigationScreenModel.Transaction.MainSale -> {
            when (stateValues.navigationTransactionSaleClientId) {
              0 -> stateValues.navigationScreensTransactionSaleRightClient1
              1 -> stateValues.navigationScreensTransactionSaleRightClient2
              2 -> stateValues.navigationScreensTransactionSaleRightClient3
              3 -> stateValues.navigationScreensTransactionSaleRightClient4
              else -> stateValues.navigationScreensTransactionSaleRightClient5
            }
          }

          is NavigationScreenModel.Transaction.MainReturn -> {
            when (stateValues.navigationTransactionReturnClientId) {
              0 -> stateValues.navigationScreensTransactionReturnRightClient1
              1 -> stateValues.navigationScreensTransactionReturnRightClient2
              2 -> stateValues.navigationScreensTransactionReturnRightClient3
              3 -> stateValues.navigationScreensTransactionReturnRightClient4
              else -> stateValues.navigationScreensTransactionReturnRightClient5
            }
          }

          is NavigationScreenModel.Transaction.MainSupply -> {
            when (stateValues.navigationTransactionSupplyClientId) {
              0 -> stateValues.navigationScreensTransactionSupplyRightClient1
              1 -> stateValues.navigationScreensTransactionSupplyRightClient2
              2 -> stateValues.navigationScreensTransactionSupplyRightClient3
              3 -> stateValues.navigationScreensTransactionSupplyRightClient4
              else -> stateValues.navigationScreensTransactionSupplyRightClient5
            }
          }

          else -> {
            emptyList()
          }
        }

      Row(
        modifier = Modifier
          .padding(8.dp)
          .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.6f)
      ) {
        repeat(5) { index ->
          val cart by getCartState(transactionTypeIndex, index).collectAsState()

          Row(
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .padding(stateValues.focusedBorderWidth)
              .clip(RoundedCornerShape(stateValues.cornerRadius))
              .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
                RoundedCornerShape(
                  stateValues.cornerRadius
                )
              )
              .background(if (clientId == index) stateValues.AccentColor else Color.Transparent)
              .clickable(
                interactionSource = remember {
                  MutableInteractionSource()
                },
                indication = ripple(color = stateValues.TextColor),
                onClick = {
                  coroutineScope.launch {
                    when (stateValues.navigationScreensMain.last()) {
                      is NavigationScreenModel.Transaction.MainSale -> {
                        Navigation.TransactionSale.setClientId(index)
                      }
                      is NavigationScreenModel.Transaction.MainReturn -> {
                        Navigation.TransactionReturn.setClientId(index)
                      }
                      is NavigationScreenModel.Transaction.MainSupply -> {
                        Navigation.TransactionSupply.setClientId(index)
                      }
                      else -> {

                      }
                    }
                  }
                }
              ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {

            CpImage(
              modifier = Modifier
                .padding(vertical = stateValues.textFieldIconPadding)
                .aspectRatio(1f, matchHeightConstraintsFirst = true),
              url = if (cart.isEmpty()) stateValues.drawablePathIconAddCart else stateValues.drawablePathIconCart,
              fallbackRes = Res.drawable._0_0,
              contentDescription = (index + 1).toString(),
              tintColor = if (clientId == index) stateValues.AccentTextColor else stateValues.TextColor
            )


            Spacer(modifier = Modifier.width(6.dp))

            Text(
              text = (index + 1).toString(),
              color = if (clientId == index) stateValues.AccentTextColor else stateValues.TextColor,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      if (stateValues.isNarrowScreen) {
        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = navigationScreensLeft.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Transaction.Cart -> {
              TransactionCartScreen()
            }

            is NavigationScreenModel.Transaction.Selection -> {
              TransactionSelectionScreen()
            }

            is NavigationScreenModel.Transaction.Payment -> {
              TransactionPaymentScreen()
            }

            is NavigationScreenModel.Transaction.ReceiptPreview -> {
              TransactionReceiptPreviewScreen()
            }

            else -> {}
          }
        }
      } else {
        Row(
          modifier = Modifier
            .weight(1f)
        ) {
          AnimatedContent(
            modifier = Modifier
              .weight(1f),
            targetState = navigationScreensLeft.last()
          ) { model ->
            when (model) {
              is NavigationScreenModel.Transaction.Cart -> {
                TransactionCartScreen()
              }

              is NavigationScreenModel.Transaction.Selection -> {
                TransactionSelectionScreen()
              }

              is NavigationScreenModel.Transaction.Payment -> {
                TransactionPaymentScreen()
              }

              is NavigationScreenModel.Transaction.ReceiptPreview -> {
                TransactionReceiptPreviewScreen()
              }

              else -> {}
            }
          }

          AnimatedContent(
            modifier = Modifier
              .weight(1f),
            targetState = navigationScreensRight.last()
          ) { model ->
            when (model) {
              is NavigationScreenModel.Transaction.Cart -> {
                TransactionCartScreen()
              }

              is NavigationScreenModel.Transaction.Selection -> {
                TransactionSelectionScreen()
              }

              is NavigationScreenModel.Transaction.Payment -> {
                TransactionPaymentScreen()
              }

              is NavigationScreenModel.Transaction.ReceiptPreview -> {
                TransactionReceiptPreviewScreen()
              }

              else -> {}
            }
          }
        }
      }
    }
  }
}


@Composable
private fun ReceiptPreviewDivider() {
  Spacer(
    modifier = Modifier
      .height(1.dp)
      .fillMaxWidth()
      .background(Color.Black)
  )
}

@Composable
private fun AppConfiguration.ReceiptPreviewText(
  text: String,
  modifier: Modifier = Modifier,
  bold: Boolean = false,
  center: Boolean = false,
  large: Boolean = false,
  color: Color = Color.Black
) {
  Text(
    text = text,
    modifier = modifier
      .fillMaxWidth(),
    color = color,
    fontSize = if (large) stateValues.accentTextSize else stateValues.textSize,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textAlign = if (center) TextAlign.Center else TextAlign.Start
  )
}

@Composable
private fun AppConfiguration.ReceiptPreviewRow(
  title: String,
  value: String,
  bold: Boolean = false,
  large: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = title,
      color = Color.Black,
      fontSize = if (large) stateValues.accentTextSize else stateValues.textSize,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
      modifier = Modifier.weight(1f)
    )

    Text(
      text = value,
      color = Color.Black,
      fontSize = if (large) stateValues.accentTextSize else stateValues.textSize,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun AppConfiguration.ReceiptPreviewHeader(
  snapshot: TransactionReceiptSnapshotDataModel
) {
  val store = snapshot.store
  val companyFormTitle = store
    ?.companyForms
    ?.firstOrNull()
    ?.name
    ?.visibleLocalizedString(stateValues.appLanguage, "")
    .orEmpty()
  val storeName = store?.name?.visibleLocalizedString(stateValues.appLanguage, "Store") ?: "Store"

  ReceiptPreviewText(
    text = "$companyFormTitle $storeName".trim(),
    bold = true,
    center = true,
    large = true
  )

  store?.location?.name?.takeIf { it.isNotBlank() }?.let {
    Spacer(modifier = Modifier.height(3.dp))
    ReceiptPreviewText(text = it, center = true)
  }

  store?.phoneNumbers?.takeIf { it.isNotEmpty() }?.let {
    Spacer(modifier = Modifier.height(3.dp))
    ReceiptPreviewText(text = "Tel: ${it.joinToString()}", center = true)
  }

  store?.emails?.takeIf { it.isNotEmpty() }?.let {
    Spacer(modifier = Modifier.height(3.dp))
    ReceiptPreviewText(text = "Email: ${it.joinToString()}", center = true)
  }

  Spacer(modifier = Modifier.height(8.dp))
  ReceiptPreviewDivider()
  Spacer(modifier = Modifier.height(8.dp))

  ReceiptPreviewText(
    text = "ТОВАРНЫЙ ЧЕК / SALES RECEIPT",
    bold = true,
    center = true
  )

  ReceiptPreviewText(
    text = snapshot.receiptTitle(stateValues.appLanguage),
    bold = true,
    center = true
  )

  Spacer(modifier = Modifier.height(8.dp))

  ReceiptPreviewRow("Receipt", snapshot.receiptNumberText(), bold = true)
  ReceiptPreviewRow("Transaction ID", snapshot.transaction.id.ifBlank { "draft" })
  ReceiptPreviewRow("Date", receiptUiDateTime(snapshot.transaction.timeMillis))

  snapshot.cashierName.takeIf { it.isNotBlank() }?.let {
    ReceiptPreviewRow("Cashier", it)
  }

  Spacer(modifier = Modifier.height(8.dp))
  ReceiptPreviewDivider()
  Spacer(modifier = Modifier.height(8.dp))
}

private fun receiptUiDateTime(timeMillis: Long): String {
  return runCatching {
    val dt = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
  }.getOrElse { timeMillis.toString() }
}

@Composable
private fun AppConfiguration.ReceiptPreviewLine(
  line: TransactionReceiptLineDataModel
) {
  val name = line.name.visibleLocalizedString(stateValues.appLanguage, "No name")
  val suffix = line.quantity.immutableUnitName.visibleLocalizedString(stateValues.appLanguage, "")
  val quantityText = line.quantity.total.run {
    if (line.quantity.roundTotal) toInt().toString() else moneyText()
  }

  ReceiptPreviewText(
    text = "${line.index + 1}. $name",
    bold = true
  )

  line.barcode.takeIf { it.isNotBlank() }?.let {
    ReceiptPreviewText(
      text = "Barcode: $it",
      color = Color.DarkGray
    )
  }

  ReceiptPreviewRow(
    title = "$quantityText $suffix x ${line.pricePerUnit.moneyText()} ${line.currencySymbol}".trim(),
    value = "${line.total.moneyText()} ${line.currencySymbol}",
    bold = true
  )

  Spacer(modifier = Modifier.height(7.dp))
}

@Composable
private fun AppConfiguration.ReceiptPreviewTotals(
  snapshot: TransactionReceiptSnapshotDataModel
) {
  val total = snapshot.totalAmount()
  val debt = snapshot.debtAmount()
  val change = snapshot.changeAmount()

  Spacer(modifier = Modifier.height(6.dp))
  ReceiptPreviewDivider()
  Spacer(modifier = Modifier.height(8.dp))

  ReceiptPreviewRow(
    title = "TOTAL",
    value = "${total.moneyText()} ${snapshot.currencySymbol}",
    bold = true,
    large = true
  )

  Spacer(modifier = Modifier.height(8.dp))

  if (snapshot.paymentDraft.paidCash > 0.0) {
    ReceiptPreviewRow(
      title = stateValues.stringCash,
      value = "${snapshot.paymentDraft.paidCash.moneyText()} ${snapshot.currencySymbol}"
    )
  }

  if (snapshot.paymentDraft.paidCard > 0.0) {
    ReceiptPreviewRow(
      title = stateValues.stringCashless,
      value = "${snapshot.paymentDraft.paidCard.moneyText()} ${snapshot.currencySymbol}"
    )
  }

  if (debt > 0.0) {
    ReceiptPreviewRow(
      title = stateValues.stringDebtors,
      value = "${debt.moneyText()} ${snapshot.currencySymbol}"
    )

    snapshot.paymentDraft.debtor?.let { debtor ->
      val debtorName = "${debtor.firstName} ${debtor.lastName}".trim()
        .ifBlank { debtor.phoneNumber.ifBlank { debtor.id } }
      ReceiptPreviewRow("Debtor", debtorName)
      debtor.phoneNumber.takeIf { it.isNotBlank() }?.let {
        ReceiptPreviewRow("Debtor phone", it)
      }
    }
  }

  if (change > 0.0) {
    ReceiptPreviewRow(
      title = "Change",
      value = "${change.moneyText()} ${snapshot.currencySymbol}"
    )
  }

  Spacer(modifier = Modifier.height(8.dp))
  ReceiptPreviewDivider()
  Spacer(modifier = Modifier.height(8.dp))

  ReceiptPreviewRow("VAT / НДС / ҚҚС", "not specified")
  ReceiptPreviewRow("Fiscal status", "non-fiscal software receipt")

  Spacer(modifier = Modifier.height(10.dp))

  ReceiptPreviewText(
    text = "Thank you / Спасибо / Рақмет",
    center = true,
    bold = true
  )
}

private fun AppConfiguration.buildTransactionReceiptLines(
  cart: List<GoodsItemInCartDataModel>,
  stock: List<GoodsItemDataModel>,
  transactionTypeIndex: Int
): List<TransactionReceiptLineDataModel> {
  return cart.mapIndexedNotNull { index, cartItem ->
    val goodsItem = stock.find { it.id == cartItem.id } ?: return@mapIndexedNotNull null
    val price = goodsItem.priceForTransaction(transactionTypeIndex)
    val currencySymbol = stateValues.globalAppConfiguration.countries
      .getCurrency(price.currency)
      ?.symbol
      ?: price.currency

    TransactionReceiptLineDataModel(
      index = index,
      goodsItemId = goodsItem.id,
      name = goodsItem.name,
      barcode = goodsItem.firstBarcode(),
      quantity = cartItem.quantity,
      pricePerUnit = price.price.toMoneyDouble(),
      currencyCode = price.currency,
      currencySymbol = currencySymbol
    )
  }
}

private fun Double.moneyText(): String {
  val fixed = roundMoney()
  val whole = fixed.toLong()
  val cents = kotlin.math.round((fixed - whole) * 100).toInt()
  return "$whole.${cents.toString().padStart(2, '0')}"
}

private fun AppConfiguration.receiptActionNotification(result: ReceiptPlatformActionResult, positiveMessage: String) {
  postInAppNotification(
    if (result.success) positiveMessage else result.message.ifBlank { "Receipt action failed" },
    if (result.success) NotificationType.Positive else NotificationType.Negative
  )
}

@Composable
fun AppConfiguration.TransactionReceiptPreviewScreen() {
  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    val context = rememberTransactionContext()

    ScreenAppBarWidget(
      title = stateValues.stringReceipt,
      iconPath = stateValues.drawablePathIconReceipt,
      onBack = {
        coroutineScope.launch {
          when (context.transactionTypeIndex) {
            0 -> Navigation.TransactionSale.pop()
            1 -> Navigation.TransactionReturn.pop()
            else -> Navigation.TransactionSupply.pop()
          }
        }
      }
    )

    val goodsInCart by getCartState(
      context.transactionTypeIndex,
      context.clientId
    ).collectAsState()

    val latestSnapshot by latestTransactionReceiptSnapshotState.collectAsState()

    val paymentDraft = getTransactionPaymentDraft(
      context.transactionTypeIndex,
      context.clientId
    )

    val liveLines = remember(goodsInCart, stateValues.stock, context.transactionTypeIndex) {
      buildTransactionReceiptLines(
        cart = goodsInCart,
        stock = stateValues.stock.orEmpty(),
        transactionTypeIndex = context.transactionTypeIndex
      )
    }

    val store = stateValues.stores?.find { it.id == stateValues.activeStoreId }

    val currencyCode = liveLines.firstOrNull()?.currencyCode
      ?: paymentDraft?.debtor?.currency
      ?: stateValues.globalAppConfiguration.countries
        .find { it.locale.equals(stateValues.userAccount?.countryLocale, true) }
        ?.currencies
        ?.firstOrNull()
        ?.code
      ?: "KZT"

    val currencySymbol = liveLines.firstOrNull()?.currencySymbol
      ?: stateValues.globalAppConfiguration.countries.getCurrency(currencyCode)?.symbol
      ?: currencyCode

    val total = liveLines.sumOf { it.total }.roundMoney()

    val draft = paymentDraft ?: TransactionPaymentDraftDataModel(
      transactionTypeIndex = context.transactionTypeIndex,
      clientId = context.clientId,
      paymentModeId = "1",
      paidCash = 0.0,
      paidCard = total,
      cardPaymentOptionId = 0
    )

    val currentTransaction = TransactionDataModel(
      id = "",
      workshiftId = 0L,
      type = transactionServerType(context.transactionTypeIndex),
      storeId = stateValues.activeStoreId.orEmpty(),
      goodsInTransaction = liveLines.map {
        GoodsItemInTransactionDataModel(
          barcode = it.barcode,
          quantity = it.quantity.total,
          pricePerUnit = it.pricePerUnit,
          supplierId = null
        )
      },
      paidCash = draft.paidCash,
      paidCard = draft.paidCard,
      cardPaymentOptionId = draft.cardPaymentOptionId,
      debtor = draft.debtor,
      timeMillis = getCurrentTimeMillis()
    )

    val cashierName = "${stateValues.userAccount?.firstName.orEmpty()} ${stateValues.userAccount?.lastName.orEmpty()}".trim()

    val snapshotForScreen =
      latestSnapshot?.takeIf {
        it.transaction.type == transactionServerType(context.transactionTypeIndex) &&
            it.paymentDraft.clientId == context.clientId
      } ?: TransactionReceiptSnapshotDataModel(
        transaction = currentTransaction,
        store = store,
        lines = liveLines,
        paymentDraft = draft,
        currencyCode = currencyCode,
        currencySymbol = currencySymbol,
        cashierName = cashierName,
        cashierPhoneNumber = stateValues.userAccount?.phoneNumber.orEmpty(),
        cashierEmail = stateValues.userAccount?.email.orEmpty()
      )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(8.dp)
        .clip(RoundedCornerShape(stateValues.cornerRadius))
        .background(Color.White)
        .border(
          stateValues.unfocusedBorderWidth,
          stateValues.PlaceholderTextColor,
          RoundedCornerShape(stateValues.cornerRadius)
        )
        .padding(stateValues.marginTextFieldGroup)
    ) {
      item {
        ReceiptPreviewHeader(snapshotForScreen)
      }

      if (snapshotForScreen.lines.isEmpty()) {
        item {
          ReceiptPreviewText(
            text = "No items",
            center = true,
            bold = true,
            color = Color.DarkGray
          )
        }
      } else {
        items(snapshotForScreen.lines) { line ->
          ReceiptPreviewLine(line)
        }
      }

      item {
        ReceiptPreviewTotals(snapshotForScreen)
        Spacer(modifier = Modifier.height(stateValues.screenHeight / 7))
      }
    }

    val alreadyCompleted = snapshotForScreen.transaction.id.isNotBlank()
    val pdfBytes = remember(snapshotForScreen, stateValues.appLanguage) {
      snapshotForScreen.buildReceiptPdfBytes(stateValues.appLanguage)
    }
    val fileName = remember(snapshotForScreen) {
      snapshotForScreen.receiptPdfFileName()
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      if (!alreadyCompleted) {
        actionButton(
          text = stateValues.stringComplete,
          enabled = snapshotForScreen.lines.isNotEmpty() && stateValues.latestNotification == null,
          iconPath = stateValues.drawablePathIconCheck,
          onClick = {
            completeTransaction(
              transaction = currentTransaction,
              transactionTypeIndex = context.transactionTypeIndex,
              clientId = context.clientId,
              receiptSnapshot = snapshotForScreen
            ) {
              coroutineScope.launch {
                when (context.transactionTypeIndex) {
                  0 -> Navigation.TransactionSale.go(
                    NavigationScreenModel.Transaction.ReceiptPreview,
                    remove = true
                  )

                  1 -> Navigation.TransactionReturn.go(
                    NavigationScreenModel.Transaction.ReceiptPreview,
                    remove = true
                  )

                  else -> Navigation.TransactionSupply.go(
                    NavigationScreenModel.Transaction.ReceiptPreview,
                    remove = true
                  )
                }
              }
            }
          }
        )
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          actionButton(
            modifier = Modifier.weight(1f),
            text = "PDF",
            iconPath = stateValues.drawablePathIconReceipt,
            onClick = {
              coroutineScope.launch {
                receiptActionNotification(
                  saveReceiptPdf(fileName, pdfBytes),
                  "Receipt PDF saved"
                )
              }
            }
          )

          actionButton(
            modifier = Modifier.weight(1f),
            text = "Share",
            iconPath = stateValues.drawablePathIconSwitch,
            onClick = {
              coroutineScope.launch {
                receiptActionNotification(
                  shareReceiptPdf(fileName, pdfBytes, whatsappOnly = false),
                  "Receipt shared"
                )
              }
            }
          )

          actionButton(
            modifier = Modifier.weight(1f),
            text = "WhatsApp",
            iconPath = stateValues.drawablePathIconSwitch,
            onClick = {
              coroutineScope.launch {
                receiptActionNotification(
                  shareReceiptPdf(fileName, pdfBytes, whatsappOnly = true),
                  "Receipt sent to WhatsApp"
                )
              }
            }
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          actionButton(
            modifier = Modifier.weight(1f),
            text = "Print",
            iconPath = stateValues.drawablePathIconDevices,
            onClick = {
              coroutineScope.launch {
                receiptActionNotification(
                  printReceiptEscPos(snapshotForScreen.buildReceiptEscPosBytes(stateValues.appLanguage)),
                  "Receipt sent to printer"
                )
              }
            }
          )

          actionButton(
            modifier = Modifier.weight(1f),
            text = "Quit",
            enabledColor = stateValues.DisabledColor,
            iconPath = stateValues.drawablePathIconExit,
            onClick = {
              coroutineScope.launch {
                latestTransactionReceiptSnapshotState.emit(null)
                when (context.transactionTypeIndex) {
                  0 -> Navigation.TransactionSale.clear()
                  1 -> Navigation.TransactionReturn.clear()
                  else -> Navigation.TransactionSupply.clear()
                }
              }
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}


//@Composable
//fun AppConfiguration.TransactionReceiptPreviewScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainSale -> {
//        0
//      }
//
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        1
//      }
//
//      else -> {
//        2
//      }
//    }
//
//    val clientId = when (transactionTypeIndex) {
//      0 -> {
//        stateValues.navigationTransactionReturnClientId
//      }
//
//      1 -> {
//        stateValues.navigationTransactionSupplyClientId
//      }
//
//      else -> {
//        stateValues.navigationTransactionSaleClientId
//      }
//    }
//
//    ScreenAppBarWidget(
//      title = stateValues.stringReceipt,
//      iconPath = stateValues.drawablePathIconReceipt,
//      onBack = if (
//        when (transactionTypeIndex) {
//          0 -> !Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//          1 -> !Navigation.TransactionReturn.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//          else -> !Navigation.TransactionSupply.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//        }
//      ) {
//        {
//          coroutineScope.launch {
//            when (transactionTypeIndex) {
//              0 -> Navigation.TransactionSale.pop()
//              1 -> Navigation.TransactionReturn.pop()
//              2 -> Navigation.TransactionSupply.pop()
//            }
//          }
//        }
//      } else null
//    )
//
//    var goodsInCart by rememberSaveable {
//      mutableStateOf(emptyList<GoodsItemInCartDataModel>())
//    }
//
//    LaunchedEffect(Unit) {
//      observeCart(transactionTypeIndex, clientId)
//        .collect {
//          it?.let {
//            goodsInCart = it
//          }
//        }
//    }
//
//    LazyColumn(
//      modifier = Modifier
//        .padding(8.dp)
//        .background(Color.White)
//        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor)
//        .weight(1f)
//    ) {
//      var totalPrice = 0.0
//
//      stateValues
//        .stores
//        ?.find {
//          it.id == stateValues.activeStoreId
//        }?.run {
//          item {
//            val ownershipFormTitle = this@run.companyForms.first().name.extractLocalizedString(stateValues.appLanguage)
//
//            Text(
//              text = "$ownershipFormTitle ${name.extractLocalizedString(stateValues.appLanguage)}",
//              modifier = Modifier
//                .padding(stateValues.marginTextFieldGroup, stateValues.marginTextFieldGroup, stateValues.marginTextFieldGroup,)
//                .fillMaxWidth(),
//              textAlign = TextAlign.Center,
//              fontWeight = FontWeight.Bold,
//              fontSize = stateValues.accentTextSize
//            )
//
//            Text(
//              text = location.name,
//              modifier = Modifier
//                .fillMaxWidth()
//                .padding(start = stateValues.marginTextFieldGroup, top = 2.dp, end = stateValues.marginTextFieldGroup, 12.dp),
//              textAlign = TextAlign.Center,
//              fontSize = stateValues.textSize
//            )
//          }
//        }
//
//      goodsInCart.forEachIndexed { index, item ->
//        stateValues.stock?.find { it.id == item.id }?.run {
//          val price =
//            when (transactionTypeIndex) {
//              0 -> supplyPrices.first().price.toDouble()
//              1 -> returnPrices.first().price.toDouble()
//              else -> supplyPrices.first().price.toDouble()
//            }  // TODO
//
//          val currencySymbol = stateValues.globalAppConfiguration.countries.getCurrency(
//            when (transactionTypeIndex) {
//              0 -> supplyPrices.first().currency
//              1 -> returnPrices.first().currency
//              else -> supplyPrices.first().currency
//            }
//          )?.symbol
//
//          totalPrice += item.quantity.total * price
//
//          item {
//            Text(
//              text = "${index + 1} ${name.extractLocalizedString(stateValues.appLanguage) ?: "No name"}", // TODO
//              modifier = Modifier
//                .padding(horizontal = stateValues.marginTextFieldGroup),
//              fontWeight = FontWeight.Bold,
//              fontSize = stateValues.textSize
//            )
//
//            Row(
//              modifier = Modifier
//                .fillMaxWidth(),
//              horizontalArrangement = Arrangement.SpaceBetween
//            ) {
//              val suffix = item.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage)
//
//              Text(
//                text = "${
//                  item.quantity.run { if (roundTotal) total.toInt() else total }
//                } $suffix x $price $currencySymbol",
//                modifier = Modifier
//                  .padding(horizontal = stateValues.marginTextFieldGroup),
//                fontSize = stateValues.textSize
//              )
//
//              Text(
//                text = "$price $currencySymbol",
//                modifier = Modifier
//                  .padding(horizontal = stateValues.marginTextFieldGroup),
//                fontSize = stateValues.accentTextSize
//              )
//            }
//          }
//        }
//      }
//    }
//
//    Column(
//      modifier = Modifier
//        .fillMaxWidth()
//        .padding(horizontal = 8.dp)
//    ) {
//      Spacer(modifier = Modifier.height(4.dp))
//
//      Row(
//        modifier = Modifier
//          .fillMaxWidth()
//      ) {
//        actionButton(
//          text = "",
//          enabledColor = stateValues.DisabledColor,
//          iconPath = stateValues.drawablePathIconAdd, // TODO
//          iconContentDescription = stateValues.stringAdd, // TODO
//          onClick = {
//
//          }
//        )
//      }
//
//      Spacer(modifier = Modifier.height(2.dp))
//
//      actionButton(
//        text = stateValues.stringComplete, // TODO
//        enabled = stateValues.latestNotification == null,
//        onClick = {
//
//        }
//      )
//
//      Spacer(modifier = Modifier.height(4.dp))
//    }
//  }
//}

@Composable
private fun AppConfiguration.TransactionTotalCard(
  title: String,
  total: Double,
  currencySymbol: String,
  currencyCode: String
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        stateValues.AccentColor,
        RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(stateValues.BackgroundColor)
      .padding(stateValues.marginTextFieldGroup)
  ) {
    if (title.isNotBlank()) {
      Text(
        text = title,
        color = stateValues.PlaceholderTextColor,
        fontSize = stateValues.smallTextSize,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(4.dp))
    }

    Text(
      text = "${total.moneyText()} $currencySymbol".trim(),
      color = stateValues.TextColor,
      fontSize = stateValues.titleTextSize,
      fontWeight = FontWeight.Bold
    )

    if (currencyCode.isNotBlank()) {
      Text(
        text = currencyCode,
        color = stateValues.PlaceholderTextColor,
        fontSize = stateValues.smallTextSize
      )
    }
  }
}

@Composable
private fun AppConfiguration.TransactionPaymentInfoCard(
  title: String,
  subtitle: String,
  amount: Double,
  currencySymbol: String
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        stateValues.PlaceholderTextColor,
        RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(stateValues.BackgroundColor)
      .padding(stateValues.marginTextFieldGroup)
  ) {
    Text(
      text = title,
      color = stateValues.TextColor,
      fontWeight = FontWeight.Bold,
      fontSize = stateValues.accentTextSize
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = subtitle,
      color = stateValues.PlaceholderTextColor,
      fontSize = stateValues.smallTextSize
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "${amount.moneyText()} $currencySymbol".trim(),
      color = stateValues.TextColor,
      fontWeight = FontWeight.Bold,
      fontSize = stateValues.titleTextSize
    )
  }
}

@Composable
private fun AppConfiguration.TransactionPaymentOptionButton(
  modifier: Modifier = Modifier,
  text: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(if (selected) stateValues.AccentColor else Color.Transparent)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(
          color = if (selected) stateValues.AccentTextColor else stateValues.TextColor
        ),
        onClick = onClick
      )
      .padding(18.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
      fontSize = stateValues.textSize,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun AppConfiguration.TransactionAmountField(
  title: String,
  value: String,
  placeholder: String,
  onValueChange: (String) -> Unit
) {
  Column {
    Text(
      text = title,
      color = stateValues.TextColor,
      fontSize = stateValues.textSize,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(4.dp))

    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = TextStyle(
        color = stateValues.TextColor,
        fontSize = stateValues.accentTextSize,
        fontWeight = FontWeight.Bold
      ),
      modifier = Modifier
        .fillMaxWidth()
        .height(stateValues.textFieldHeight)
        .clip(RoundedCornerShape(stateValues.cornerRadius))
        .border(
          stateValues.unfocusedBorderWidth,
          stateValues.PlaceholderTextColor,
          RoundedCornerShape(stateValues.cornerRadius)
        )
        .background(stateValues.BackgroundColor)
        .padding(horizontal = stateValues.marginTextFieldGroup),
      decorationBox = { innerTextField ->
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.CenterStart
        ) {
          if (value.isBlank()) {
            Text(
              text = placeholder,
              color = stateValues.PlaceholderTextColor,
              fontSize = stateValues.textSize
            )
          }

          innerTextField()
        }
      }
    )
  }
}


private fun paymentInputAppend(current: String, token: String): String {
  val clean = current.trim().replace(',', '.')

  if (token == "⌫")
    return clean.dropLast(1)

  if (token == ".") {
    return if (clean.contains('.')) clean else clean.ifBlank { "0" } + "."
  }

  if (!token.all { it.isDigit() })
    return clean

  val next = if (clean == "0") token else clean + token
  val decimals = next.substringAfter('.', "")

  return if (next.contains('.') && decimals.length > 2)
    clean
  else
    next
}

private fun paymentInputNormalize(value: String): String {
  val clean = value.trim().replace(',', '.')

  if (clean.isBlank() || clean == ".")
    return ""

  val number = clean.toDoubleOrNull() ?: return clean.dropLast(1)
  val limited = kotlin.math.floor(number * 100.0) / 100.0

  return when {
    clean.endsWith(".") -> clean
    clean.contains('.') -> {
      val before = limited.toString().substringBefore('.')
      val after = clean.substringAfter('.', "").take(2)
      "$before.$after"
    }
    else -> limited.toLong().toString()
  }
}

private fun moneyInputFromDouble(value: Double): String {
  val rounded = kotlin.math.floor(value.coerceAtLeast(0.0) * 100.0) / 100.0
  val whole = rounded.toLong()
  val cents = kotlin.math.round((rounded - whole) * 100.0).toInt()
  return "$whole.${cents.toString().padStart(2, '0')}"
}

private fun List<LocalizedStringDataModel>.visibleLocalizedString(
  language: String,
  fallback: String
): String {
  return extractLocalizedString(language)
    ?.trim()
    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    ?: firstOrNull { it.language.equals("main", ignoreCase = true) && it.value.trim().isNotBlank() && !it.value.trim().equals("null", ignoreCase = true) }
      ?.value
      ?.trim()
    ?: firstOrNull { it.value.trim().isNotBlank() && !it.value.trim().equals("null", ignoreCase = true) }
      ?.value
      ?.trim()
    ?: fallback
}

private fun AppConfiguration.goodsCategoryName(
  categoryId: String
): String? {
  return stateValues.goodsCategories
    .orEmpty()
    .find { it.id == categoryId }
    ?.name
    ?.visibleLocalizedString(stateValues.appLanguage, categoryId)
}

private fun cashTenderShortcutBase(currencyCode: String): List<Double> {
  return when (currencyCode.uppercase()) {
    "KZT" -> listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 3000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0)
    "RUB" -> listOf(50.0, 100.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)
    "USD" -> listOf(1.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    "EUR" -> listOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    else -> listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)
  }
}

private fun quickTenderAmounts(
  amount: Double,
  currencyCode: String,
  includeExactRemaining: Boolean
): List<Double> {
  val target = amount.roundMoney().coerceAtLeast(0.0)
  if (target <= 0.0)
    return emptyList()

  val result = mutableListOf<Double>()

  if (includeExactRemaining)
    result += target

  cashTenderShortcutBase(currencyCode)
    .filter { it >= target && it !in result }
    .forEach { result += it }

  if (result.size < 4) {
    var next = cashTenderShortcutBase(currencyCode).lastOrNull()?.takeIf { it > 0.0 } ?: 10000.0
    while (result.size < 4) {
      next *= 2.0
      if (next !in result)
        result += next
    }
  }

  return result.distinct().take(4)
}

@Composable
private fun AppConfiguration.TransactionQuickAmountButtons(
  targetAmount: Double,
  currencyCode: String,
  currencySymbol: String,
  includeExactRemaining: Boolean,
  onAmountSelected: (Double) -> Unit
) {
  val amounts = quickTenderAmounts(targetAmount, currencyCode, includeExactRemaining)

  if (amounts.isEmpty())
    return

  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    items(amounts) { amount ->
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(stateValues.cornerRadius))
          .border(
            stateValues.unfocusedBorderWidth,
            stateValues.AccentColor,
            RoundedCornerShape(stateValues.cornerRadius)
          )
          .background(stateValues.BackgroundColor)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = stateValues.AccentColor)
          ) {
            onAmountSelected(amount)
          }
          .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "${moneyInputFromDouble(amount)} $currencySymbol",
          color = stateValues.AccentColor,
          fontSize = stateValues.smallTextSize,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun AppConfiguration.TransactionPaymentAmountField(
  modifier: Modifier = Modifier,
  title: String,
  value: String,
  placeholder: String = "0.00",
  selected: Boolean,
  leadingIconPath: String,
  onSelected: () -> Unit,
  onValueChange: (String) -> Unit
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val suppressSystemKeyboard = getPlatformName().contains("android", ignoreCase = true)

  genericTextField(
    modifier = modifier,
    titleText = title,
    valueInitial = value,
    placeholderText = placeholder,
    leadingIconPath = leadingIconPath,
    keyboardType = KeyboardType.Decimal,
    imeWithAction = ImeWithAction(ImeAction.Done),
    readOnly = suppressSystemKeyboard,
    showClearButton = true,
    titleTextColor = if (selected) stateValues.AccentColor else stateValues.TextColor,
    focusedBorderColor = stateValues.AccentColor,
    unfocusedBorderColor = if (selected) stateValues.AccentColor else stateValues.TextColor,
    selectionBackgroundColor = stateValues.AccentColor,
    selectionFocusTextColor = stateValues.AccentTextColor,
    updateIsFocusedAction = { focusState ->
      if (focusState.isFocused) {
        onSelected()

        if (suppressSystemKeyboard)
          keyboardController?.hide()
      }
    },
    onValueChange = { rawValue, applyChange ->
      val normalized = rawValue.trim().replace(',', '.')
      val isAcceptable = normalized.isEmpty() ||
          normalized == "." ||
          normalized.matches(Regex("^\\d*(\\.\\d{0,2})?$"))

      if (isAcceptable) {
        onValueChange(normalized)
        applyChange()
      }
    }
  )
}


@Composable
private fun AppConfiguration.TransactionNumpad(
  modifier: Modifier = Modifier,
  onInput: (String) -> Unit
) {
  val rows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫")
  )

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    rows.forEach { row ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        row.forEach { token ->
          Box(
            modifier = Modifier
              .weight(1f)
              .height((stateValues.textFieldHeight.value * 0.9f).dp)
              .clip(RoundedCornerShape(stateValues.cornerRadius))
              .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
              )
              .background(if (token == "⌫") stateValues.DisabledColor else stateValues.BackgroundColor)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.TextColor)
              ) {
                onInput(token)
              },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = token,
              color = stateValues.TextColor,
              fontSize = stateValues.accentTextSize,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AppConfiguration.DebtorPaymentCard(
  debtor: DebtorDataModel,
  selected: Boolean = false,
  onClick: () -> Unit,
  onDelete: (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(color = if (selected) stateValues.AccentTextColor else stateValues.TextColor),
        onClick = onClick
      )
      .padding(stateValues.marginTextFieldGroup),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "${debtor.firstName} ${debtor.lastName}".trim().ifBlank { debtor.phoneNumber.ifBlank { "Unnamed debtor" } },
        color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      debtor.phoneNumber.takeIf { it.isNotBlank() }?.let {
        Text(
          text = it,
          color = if (selected) stateValues.AccentTextColor else stateValues.PlaceholderTextColor,
          fontSize = stateValues.smallTextSize,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Text(
      text = "${moneyInputFromDouble(debtor.debtAmount)} ${debtor.currency}",
      color = if (selected) stateValues.AccentTextColor else stateValues.AccentColor,
      fontSize = stateValues.textSize,
      fontWeight = FontWeight.Bold
    )

    onDelete?.let {
      Spacer(modifier = Modifier.width(8.dp))

      actionButton(
        text = "",
        fillMaxWidthIfTextPresent = false,
        enabledColor = stateValues.ErrorColor,
        iconPath = stateValues.drawablePathIconCancel,
        iconContentDescription = stateValues.stringDelete,
        onClick = it
      )
    }
  }
}


@Composable
private fun AppConfiguration.TransactionPlainTextField(
  title: String,
  value: String,
  placeholder: String,
  leadingIconPath: String? = null,
  keyboardType: KeyboardType = KeyboardType.Text,
  onValueChange: (String) -> Unit
) {
  genericTextField(
    titleText = title,
    valueInitial = value,
    placeholderText = placeholder,
    leadingIconPath = leadingIconPath,
    keyboardType = keyboardType,
    imeWithAction = ImeWithAction(ImeAction.Next),
    showClearButton = true,
    onValueChange = { rawValue, applyChange ->
      onValueChange(rawValue)
      applyChange()
    }
  )
}



@Composable
private fun AppConfiguration.TransactionPaymentHeadsUpCard(
  total: Double,
  paid: Double,
  debt: Double,
  remaining: Double,
  change: Double,
  currencySymbol: String,
  currencyCode: String,
  paymentValid: Boolean
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = stateValues.marginTextField)
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        if (paymentValid) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(stateValues.BackgroundColor)
      .padding(stateValues.marginTextFieldGroup)
  ) {
    Text(
      text = "${total.moneyText()} $currencySymbol".trim(),
      color = stateValues.TextColor,
      fontSize = stateValues.titleTextSize,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    if (currencyCode.isNotBlank()) {
      Text(
        text = currencyCode,
        color = stateValues.PlaceholderTextColor,
        fontSize = stateValues.smallTextSize,
        fontWeight = FontWeight.Bold
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    TransactionPaymentSummaryRows(
      paid = paid,
      debt = debt,
      remaining = remaining,
      change = change,
      currencySymbol = currencySymbol,
      large = true,
      paymentValid = paymentValid
    )
  }
}

@Composable
private fun AppConfiguration.TransactionPaymentSummaryRows(
  paid: Double,
  debt: Double,
  remaining: Double,
  change: Double,
  currencySymbol: String,
  large: Boolean = false,
  paymentValid: Boolean = true
) {
  val textSize = if (large) stateValues.textSize else stateValues.smallTextSize
  val valueSize = if (large) stateValues.accentTextSize else stateValues.textSize

  @Composable
  fun SummaryRow(
    title: String,
    value: Double,
    valueColor: Color = stateValues.TextColor
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = title,
        color = stateValues.PlaceholderTextColor,
        fontSize = textSize,
        fontWeight = FontWeight.Bold
      )

      Text(
        text = "${moneyInputFromDouble(value)} $currencySymbol",
        color = valueColor,
        fontSize = valueSize,
        fontWeight = FontWeight.Bold
      )
    }
  }

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    SummaryRow(
      title = "Paid",
      value = paid,
      valueColor = stateValues.TextColor
    )

    if (debt > 0.0) {
      Spacer(modifier = Modifier.height(4.dp))
      SummaryRow(
        title = "Debt",
        value = debt,
        valueColor = stateValues.AccentColor
      )
    }

    if (remaining > 0.0) {
      Spacer(modifier = Modifier.height(4.dp))
      SummaryRow(
        title = "Remaining",
        value = remaining,
        valueColor = stateValues.ErrorColor
      )
    }

    if (change > 0.0) {
      Spacer(modifier = Modifier.height(4.dp))
      SummaryRow(
        title = "Change",
        value = change,
        valueColor = stateValues.AccentColor
      )
    }
  }
}

@Composable
private fun AppConfiguration.TransactionDebtText(
  debtAmount: Double,
  currencySymbol: String
) {
  Text(
    text = "Debt: ${moneyInputFromDouble(debtAmount)} $currencySymbol",
    color = if (debtAmount > 0.0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
    fontSize = stateValues.textSize,
    fontWeight = FontWeight.Bold
  )
}

@Composable
fun AppConfiguration.TransactionPaymentScreen() {
  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    val context = rememberTransactionContext()

    ScreenAppBarWidget(
      title = stateValues.stringPayment,
      onBack = {
        coroutineScope.launch {
          when (context.transactionTypeIndex) {
            0 -> Navigation.TransactionSale.pop()
            1 -> Navigation.TransactionReturn.pop()
            else -> Navigation.TransactionSupply.pop()
          }
        }
      }
    )

    val goodsInCart by getCartState(
      context.transactionTypeIndex,
      context.clientId
    ).collectAsState()

    val lines = remember(
      goodsInCart,
      stateValues.stock,
      context.transactionTypeIndex,
      stateValues.appLanguage
    ) {
      buildTransactionReceiptLines(
        cart = goodsInCart,
        stock = stateValues.stock.orEmpty(),
        transactionTypeIndex = context.transactionTypeIndex
      )
    }

    val total = remember(lines) {
      lines.sumOf { it.total }.roundMoney()
    }

    val country = stateValues.globalAppConfiguration.countries.find {
      it.locale.equals(stateValues.userAccount?.countryLocale, true)
    } ?: stateValues.globalAppConfiguration.countries.first()

    val currencyCode = lines.firstOrNull()?.currencyCode
      ?: country.currencies.firstOrNull()?.code
      ?: "KZT"

    val currencySymbol = lines.firstOrNull()?.currencySymbol
      ?: country.currencies.firstOrNull()?.symbol
      ?: currencyCode

    LaunchedEffect(stateValues.activeStoreId) {
      stateValues.activeStoreId?.let { getDebtors(it) }
    }

    val debtors by debtorsState.payload.collectAsState()

    var selectedCashlessPaymentMethodId by rememberSaveable {
      mutableStateOf(country.preferredCashlessPaymentOptionId)
    }

    var cashText by rememberSaveable(context.transactionTypeIndex, context.clientId) {
      mutableStateOf("")
    }

    var cardText by rememberSaveable(context.transactionTypeIndex, context.clientId) {
      mutableStateOf("")
    }

    var debtText by rememberSaveable(context.transactionTypeIndex, context.clientId) {
      mutableStateOf("")
    }

    var activeAmountField by rememberSaveable {
      mutableStateOf("cash")
    }

    var selectedDebtorId by rememberSaveable {
      mutableStateOf<String?>(null)
    }

    var newDebtorFirstName by rememberSaveable { mutableStateOf("") }
    var newDebtorLastName by rememberSaveable { mutableStateOf("") }
    var newDebtorPhone by rememberSaveable { mutableStateOf("") }
    var newDebtorEmail by rememberSaveable { mutableStateOf("") }

    val paymentScreenState by context.stateHost.state.collectAsState()
    val paymentModeStateKey = "payment_mode_${context.transactionTypeIndex}_${context.clientId}"

    val paymentMode = tabRowWidget(
      modifier = Modifier.padding(
        start = stateValues.marginTextField,
        top = stateValues.marginTextField,
        end = stateValues.marginTextField
      ),
      selectedIndexInitial = paymentScreenState[paymentModeStateKey] ?: "0",
      tabs = listOf(
        TabContent("0", stateValues.stringCash) {
          coroutineScope.launch { context.stateHost.setState(paymentModeStateKey to it) }
        },
        TabContent("1", stateValues.stringCashless) {
          coroutineScope.launch { context.stateHost.setState(paymentModeStateKey to it) }
        },
        TabContent("2", stateValues.stringMixed) {
          coroutineScope.launch { context.stateHost.setState(paymentModeStateKey to it) }
        }
      )
    )

    LaunchedEffect(paymentMode.id, total) {
      when (paymentMode.id) {
        "0" -> {
          cashText = moneyInputFromDouble(total)
          cardText = ""
          debtText = ""
          activeAmountField = "cash"
        }

        "1" -> {
          cashText = ""
          cardText = moneyInputFromDouble(total)
          debtText = ""
          activeAmountField = "card"
        }

        else -> {
          cashText = ""
          cardText = ""
          debtText = ""
          activeAmountField = "cash"
        }
      }
    }

    fun rawPaymentValue(field: String): String {
      return when (field) {
        "card" -> cardText
        "debt" -> debtText
        else -> cashText
      }
    }

    fun fieldMax(field: String): Double {
      if (paymentMode.id != "2")
        return Double.POSITIVE_INFINITY

      val cash = if (field == "cash") 0.0 else cashText.toMoneyDouble()
      val card = if (field == "card") 0.0 else cardText.toMoneyDouble()
      val debt = if (field == "debt") 0.0 else debtText.toMoneyDouble()

      return (total - cash - card - debt).coerceAtLeast(0.0).roundMoney()
    }

    fun setPaymentField(field: String, rawValue: String) {
      val normalized = paymentInputNormalize(rawValue)
      val numericValue = normalized.toMoneyDouble()
      val maxValue = fieldMax(field)

      val finalText = if (paymentMode.id == "2" && numericValue > maxValue) {
        moneyInputFromDouble(maxValue)
      } else {
        normalized
      }

      when (field) {
        "card" -> cardText = finalText
        "debt" -> debtText = finalText
        else -> cashText = finalText
      }
    }

    fun setActiveAmount(value: Double) {
      setPaymentField(activeAmountField, moneyInputFromDouble(value))
    }

    fun applyNumpadToken(token: String) {
      val current = rawPaymentValue(activeAmountField)
      setPaymentField(activeAmountField, paymentInputAppend(current, token))
    }

    val paidCash = when (paymentMode.id) {
      "1" -> 0.0
      else -> cashText.toMoneyDouble()
    }.roundMoney()

    val paidCard = when (paymentMode.id) {
      "0" -> 0.0
      "1" -> total
      else -> cardText.toMoneyDouble()
    }.roundMoney()

    val debtAmount = when (paymentMode.id) {
      "2" -> debtText.toMoneyDouble()
      else -> 0.0
    }.roundMoney()

    val selectedDebtor = debtors.orEmpty().find { it.id == selectedDebtorId }

    val draftDebtor = if (debtAmount > 0.0) {
      selectedDebtor?.copy(
        debtAmount = debtAmount,
        currency = currencyCode
      ) ?: DebtorDataModel(
        firstName = newDebtorFirstName.trim(),
        lastName = newDebtorLastName.trim(),
        phoneNumber = newDebtorPhone.trim(),
        email = newDebtorEmail.trim(),
        debtAmount = debtAmount,
        currency = currencyCode
      )
    } else null

    val debtorValid = debtAmount <= 0.0 || (
        selectedDebtor != null ||
            newDebtorFirstName.trim().isNotBlank() ||
            newDebtorLastName.trim().isNotBlank() ||
            newDebtorPhone.trim().isNotBlank() ||
            newDebtorEmail.trim().isNotBlank()
        )

    val paymentSum = (paidCash + paidCard + debtAmount).roundMoney()
    val remainingAmount = (total - paymentSum).coerceAtLeast(0.0).roundMoney()
    val paidMoneyAmount = (paidCash + paidCard).roundMoney()
    val cashRequiredAmount = (total - paidCard - debtAmount).coerceAtLeast(0.0).roundMoney()
    val changeAmount = (paidCash - cashRequiredAmount).coerceAtLeast(0.0).roundMoney()

    val paymentValid = goodsInCart.isNotEmpty() &&
        total > 0.0 &&
        paidCash >= 0.0 &&
        paidCard >= 0.0 &&
        debtAmount >= 0.0 &&
        paymentSum >= total &&
        debtorValid

    fun targetForField(field: String): Double {
      return when (field) {
        "card" -> (total - paidCash - debtAmount).coerceAtLeast(0.0)
        "debt" -> (total - paidCash - paidCard).coerceAtLeast(0.0)
        else -> (total - paidCard - debtAmount).coerceAtLeast(0.0)
      }.roundMoney()
    }

    Spacer(modifier = Modifier.height(stateValues.marginTextField))

    TransactionPaymentHeadsUpCard(
      total = total,
      paid = paidMoneyAmount,
      debt = debtAmount,
      remaining = remainingAmount,
      change = changeAmount,
      currencySymbol = currencySymbol,
      currencyCode = currencyCode,
      paymentValid = paymentValid
    )

    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = stateValues.marginTextField)
    ) {
      when (paymentMode.id) {
        "0" -> {
          item {
            TransactionPaymentAmountField(
              title = stateValues.stringCash,
              value = cashText,
              selected = activeAmountField == "cash",
              leadingIconPath = stateValues.drawablePathIconFinances,
              onSelected = { activeAmountField = "cash" },
              onValueChange = { setPaymentField("cash", it) }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            TransactionQuickAmountButtons(
              targetAmount = total,
              currencyCode = currencyCode,
              currencySymbol = currencySymbol,
              includeExactRemaining = true,
              onAmountSelected = {
                activeAmountField = "cash"
                setPaymentField("cash", moneyInputFromDouble(it))
              }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            TransactionNumpad(
              onInput = {
                activeAmountField = "cash"
                applyNumpadToken(it)
              }
            )
          }
        }

        "1" -> {
          item {
            Text(
              text = stateValues.stringCashless,
              color = stateValues.TextColor,
              fontSize = stateValues.accentTextSize,
              fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))
          }

          items(country.cashlessPaymentOptions.chunked(2)) { row ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
              row.forEach { option ->
                TransactionPaymentOptionButton(
                  modifier = Modifier.weight(1f),
                  text = option.name.extractLocalizedString(stateValues.appLanguage) ?: option.id,
                  selected = selectedCashlessPaymentMethodId == option.id
                ) {
                  selectedCashlessPaymentMethodId = option.id
                }
              }

              repeat(2 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextField))
          }
        }

        else -> {
          item {
            TransactionPaymentAmountField(
              title = stateValues.stringCash,
              value = cashText,
              selected = activeAmountField == "cash",
              leadingIconPath = stateValues.drawablePathIconFinances,
              onSelected = { activeAmountField = "cash" },
              onValueChange = { setPaymentField("cash", it) }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            TransactionQuickAmountButtons(
              targetAmount = targetForField("cash"),
              currencyCode = currencyCode,
              currencySymbol = currencySymbol,
              includeExactRemaining = true,
              onAmountSelected = {
                activeAmountField = "cash"
                setPaymentField("cash", moneyInputFromDouble(it))
              }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            TransactionPaymentAmountField(
              title = stateValues.stringCashless,
              value = cardText,
              selected = activeAmountField == "card",
              leadingIconPath = stateValues.drawablePathIconFinances,
              onSelected = { activeAmountField = "card" },
              onValueChange = { setPaymentField("card", it) }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            TransactionQuickAmountButtons(
              targetAmount = targetForField("card"),
              currencyCode = currencyCode,
              currencySymbol = currencySymbol,
              includeExactRemaining = true,
              onAmountSelected = {
                activeAmountField = "card"
                setPaymentField("card", moneyInputFromDouble(it))
              }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            TransactionPaymentAmountField(
              title = stateValues.stringDebtors,
              value = debtText,
              selected = activeAmountField == "debt",
              leadingIconPath = stateValues.drawablePathIconDebtors,
              onSelected = { activeAmountField = "debt" },
              onValueChange = { setPaymentField("debt", it) }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            TransactionQuickAmountButtons(
              targetAmount = targetForField("debt"),
              currencyCode = currencyCode,
              currencySymbol = currencySymbol,
              includeExactRemaining = true,
              onAmountSelected = {
                activeAmountField = "debt"
                setPaymentField("debt", moneyInputFromDouble(it))
              }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            TransactionDebtText(
              debtAmount = debtAmount,
              currencySymbol = currencySymbol
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            TransactionNumpad(
              onInput = ::applyNumpadToken
            )

            if (debtAmount > 0.0) {
              Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

              Text(
                text = "Select debtor",
                color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold
              )

              Spacer(modifier = Modifier.height(stateValues.marginTextField))
            }
          }

          if (debtAmount > 0.0) {
            items(debtors.orEmpty().filter { it.debtAmount >= 0.0 }) { debtor ->
              DebtorPaymentCard(
                debtor = debtor,
                selected = debtor.id == selectedDebtorId,
                onClick = {
                  selectedDebtorId = if (selectedDebtorId == debtor.id) null else debtor.id
                }
              )

              Spacer(modifier = Modifier.height(stateValues.marginTextField))
            }

            item {
              Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

              Text(
                text = "Or add debtor for this transaction",
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
              )

              Spacer(modifier = Modifier.height(stateValues.marginTextField))

              if (selectedDebtorId == null) {
                TransactionPlainTextField(
                  title = stateValues.stringFirstName,
                  value = newDebtorFirstName,
                  placeholder = stateValues.stringFirstName,
                  leadingIconPath = stateValues.drawablePathIconPerson,
                  onValueChange = { newDebtorFirstName = it }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                TransactionPlainTextField(
                  title = stateValues.stringLastName,
                  value = newDebtorLastName,
                  placeholder = stateValues.stringLastName,
                  leadingIconPath = stateValues.drawablePathIconPerson,
                  onValueChange = { newDebtorLastName = it }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                TransactionPlainTextField(
                  title = stateValues.stringPhoneNumber,
                  value = newDebtorPhone,
                  placeholder = stateValues.stringPhoneNumber,
                  leadingIconPath = stateValues.drawablePathIconPerson,
                  keyboardType = KeyboardType.Phone,
                  onValueChange = { newDebtorPhone = it.filter { ch -> ch.isDigit() || ch == '+' } }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                TransactionPlainTextField(
                  title = stateValues.stringEmail,
                  value = newDebtorEmail,
                  placeholder = stateValues.stringEmail,
                  leadingIconPath = stateValues.drawablePathIconEmail,
                  keyboardType = KeyboardType.Email,
                  onValueChange = { newDebtorEmail = it }
                )
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        TransactionPaymentSummaryRows(
          paid = paidMoneyAmount,
          debt = debtAmount,
          remaining = remainingAmount,
          change = changeAmount,
          currencySymbol = currencySymbol,
          large = false,
          paymentValid = paymentValid
        )

        Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      actionButton(
        text = stateValues.stringReceipt,
        enabled = paymentValid && stateValues.latestNotification == null,
        onClick = {
          val draft = TransactionPaymentDraftDataModel(
            transactionTypeIndex = context.transactionTypeIndex,
            clientId = context.clientId,
            paymentModeId = paymentMode.id,
            paidCash = paidCash,
            paidCard = paidCard,
            cardPaymentOptionId = selectedCashlessPaymentMethodId.toIntOrNull() ?: 0,
            debtor = draftDebtor
          )

          setTransactionPaymentDraft(draft)

          coroutineScope.launch {
            when (context.transactionTypeIndex) {
              0 -> Navigation.TransactionSale.go(NavigationScreenModel.Transaction.ReceiptPreview)
              1 -> Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.ReceiptPreview)
              else -> Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.ReceiptPreview)
            }
          }
        }
      )

      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}


//@Composable
//fun AppConfiguration.TransactionPaymentScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainSale -> {
//        0
//      }
//
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        1
//      }
//
//      else -> {
//        2
//      }
//    }
//
//    val clientId = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        stateValues.navigationTransactionReturnClientId
//      }
//
//      is NavigationScreenModel.Transaction.MainSupply -> {
//        stateValues.navigationTransactionSupplyClientId
//      }
//
//      else -> {
//        stateValues.navigationTransactionSaleClientId
//      }
//    }
//
//    ScreenAppBarWidget(
//      title = stateValues.stringPayment,
//      onBack = if (
//        when (transactionTypeIndex) {
//          0 -> !Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//          1 -> !Navigation.TransactionReturn.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//          else -> !Navigation.TransactionSupply.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
//        }
//      ) {
//        {
//          coroutineScope.launch {
//            when (transactionTypeIndex) {
//              0 -> Navigation.TransactionSale.pop()
//              1 -> Navigation.TransactionReturn.pop()
//              2 -> Navigation.TransactionSupply.pop()
//            }
//          }
//        }
//      } else null
//    )
//
//    val goodsInCart by getCartState(transactionTypeIndex, clientId).collectAsState()
//
//    var selectedCashlessPaymentMethodId by rememberSaveable {
//      mutableStateOf(stateValues.globalAppConfiguration.countries.find {
//        it.locale.equals(stateValues.userAccount?.countryLocale, true)
//      }?.preferredCashlessPaymentOptionId ?: "0")
//    }
//
//    Column(
//      modifier = Modifier
//        .fillMaxWidth()
//        .weight(1f)
//    ) {
//      val scopeRowContent = tabRowWidget(
//        modifier = Modifier
//          .padding(stateValues.marginTextField),
//        selectedIndexInitial = "1",
//        tabs = listOf(
//          TabContent("0", stateValues.stringCash),
//          TabContent("1", stateValues.stringCashless),
//          TabContent("2", stateValues.stringMixed)
//        )
//      )
//
//      when (scopeRowContent.id) {
//        "0" -> {
//
//        }
//
//        "1" -> {
//          Column(
//            modifier = Modifier
//              .weight(1f)
//              .fillMaxSize()
//              .padding(stateValues.marginTextField),
//          ) {
//            LazyVerticalGrid(columns = GridCells.Fixed(2)) {
//              stateValues.globalAppConfiguration.countries.find {
//                it.locale.equals(stateValues.userAccount?.countryLocale, true)
//              }?.cashlessPaymentOptions?.forEach { item ->
//                item {
//                  Box(
//                    modifier = Modifier
//                      .weight(1f)
//                      .clip(RoundedCornerShape(stateValues.cornerRadius))
//                      .border(
//                        stateValues.unfocusedBorderWidth,
//                        stateValues.PlaceholderTextColor,
//                        RoundedCornerShape(
//                          stateValues.cornerRadius
//                        ),
//                      )
//                      .background(if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentColor else Color.Transparent)
//                      .clickable(
//                        interactionSource = remember {
//                          MutableInteractionSource()
//                        },
//                        indication = ripple(color = if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentTextColor else stateValues.TextColor),
//                        onClick = {
//                          selectedCashlessPaymentMethodId = item.id
//                        }
//                      ),
//                    contentAlignment = Alignment.Center
//                  ) {
//                    item.name.extractLocalizedString(stateValues.appLanguage)?.let { text ->
//                      Text(
//                        text = text,
//                        modifier = Modifier
//                          .padding(24.dp),
//                        color = if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentTextColor else stateValues.TextColor
//                      )
//                    }
//                  }
//                }
//              }
//            }
//          }
//        }
//
//        else -> {
//
//        }
//      }
//    }
//
//    if (goodsInCart.isNotEmpty())
//      Column(
//        modifier = Modifier
//          .fillMaxWidth()
//          .padding(horizontal = 8.dp)
//      ) {
//        Spacer(modifier = Modifier.height(4.dp))
//
//        actionButton(
//          text = stateValues.stringReceipt,
//          enabled = stateValues.latestNotification == null,
//          onClick = {
//            coroutineScope.launch {
//              when (transactionTypeIndex) {
//                0 -> {
//                  Navigation.TransactionSale.go(NavigationScreenModel.Transaction.ReceiptPreview)
//                }
//
//                1 -> {
//                  Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.ReceiptPreview)
//                }
//
//                else -> {
//                  Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.ReceiptPreview)
//                }
//
//              }
//            }
//          }
//        )
//
//        Spacer(modifier = Modifier.height(4.dp))
//      }
//  }
//}

@Composable
fun AppConfiguration.GoodsItemInCartWidget(
  modifier: Modifier = Modifier,
  index: Int? = null,
  goodsItemInCart: GoodsItemInCartDataModel,
  goodsItem: GoodsItemDataModel,
  textColor: Color = stateValues.TextColor,
  onClick: ((GoodsItemDataModel) -> Unit)? = null,
  onDelete: ((GoodsItemDataModel) -> Unit)? = null,
  increaseQuantityAction: () -> Unit,
  decreaseQuantityAction: () -> Unit
) {
  Row(
    modifier
      .padding(bottom = 4.dp)
      .fillMaxHeight()
      .run {
        onClick?.run {
          clickable(
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = textColor, radius = stateValues.cornerRadius),
            onClick = {
              this(goodsItem)
            }
          )
        } ?: this
      }
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        stateValues.PlaceholderTextColor,
        RoundedCornerShape(
          stateValues.cornerRadius
        )
      )
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(16.dp),
    ) {
      val itemName = goodsItem.name.visibleLocalizedString(stateValues.appLanguage, "Unnamed item")

      Text(
        text = index?.run { "${index + 1}.  $itemName" } ?: itemName,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        color = textColor
      )

      Spacer(
        modifier = Modifier
          .height(6.dp)
      )

      goodsItem.barcodes.run {
        if (size == 1) {
          this[0]
        } else {
          foldIndexed("") { index, acc, item ->
            if (index == 0)
              item
            else
              "$acc, $item"
          }
        }
      }.run {
        Text(
          text = this,
          fontSize = stateValues.textSize,
          color = textColor
        )
      }

      goodsItem.categoryIds
        .mapNotNull { goodsCategoryName(it) }
        .joinToString(", ")
        .takeIf { it.isNotBlank() }
        ?.run {
          Text(
            text = this,
            fontSize = stateValues.textSize,
            color = textColor
          )
        }

      Spacer(
        modifier = Modifier
          .height(6.dp)
      )

      Row(
        modifier = Modifier
          .height((stateValues.textFieldHeight.value / 1.2).dp)
      ) {
        actionButton(
          fillMaxHeight = true,
          text = "",
          enabledColor = stateValues.DisabledColor,
          iconPath = stateValues.drawablePathIconSubtract,
          iconContentDescription = stateValues.stringSubtract,
          onClick = decreaseQuantityAction
        )

        Spacer(modifier = Modifier.width(2.dp))

        Box(
          modifier = Modifier
            .height(stateValues.textFieldHeight)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            modifier = Modifier
              .padding(horizontal = stateValues.textFieldIconPadding),
            text = "${goodsItemInCart.quantity.total.run { if (goodsItemInCart.quantity.roundTotal) toInt() else this }} ${goodsItemInCart.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage)}",
            fontWeight = FontWeight.Bold,
            color = stateValues.TextColor
          )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // TODO: Add red and green quantity dependent coloring of buttons

        actionButton(
          fillMaxHeight = true,
          text = "",
          enabledColor = stateValues.DisabledColor,
          iconPath = stateValues.drawablePathIconAdd,
          iconContentDescription = stateValues.stringAdd,
          onClick = increaseQuantityAction
        )
      }
    }

    Column(
      modifier = Modifier
        .padding(end = 16.dp, top = 16.dp, start = 8.dp, bottom = 16.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      onDelete?.let {
        actionButton(
          text = "",
          enabledColor = stateValues.ErrorColor,
          iconPath = stateValues.drawablePathIconCancel,
          iconContentDescription = stateValues.stringDelete,
        ) {
          onDelete(goodsItem)
        }
      }

//      Spacer(
//        modifier = Modifier
//          .height(stateValues.marginTextField)
//      )
//
//      onEdit?.let {
//        actionButton(
//          text = "",
//          iconPath = stateValues.drawablePathIconEdit,
//          iconContentDescription = stateValues.drawablePathIconEdit,
//        ) {
//          onEdit(goodsItem)
//        }
//      }
    }
  }
}

@Composable
fun AppConfiguration.TransactionCartScreen() {
  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    val context = rememberTransactionContext()

    ScreenAppBarWidget(
      title = transactionTitle(
        context.transactionTypeIndex,
        stateValues.stringSale,
        stateValues.stringReturn,
        stateValues.stringSupply
      ),
      iconPath = when (context.transactionTypeIndex) {
        0 -> stateValues.drawablePathIconTransactionSale
        1 -> stateValues.drawablePathIconTransactionReturn
        else -> stateValues.drawablePathIconTransactionSupply
      }
    )

    val goodsInCart by getCartState(
      context.transactionTypeIndex,
      context.clientId
    ).collectAsState()

    if (goodsInCart.isEmpty()) {
      MessageText(
        modifier = Modifier.fillMaxSize(),
        text = stateValues.stringCartEmpty
      )
    } else {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .padding(stateValues.marginTextField)
      ) {
        itemsIndexed(goodsInCart) { index, cartItem ->
          val goodsItem = stateValues.stock?.find { it.id == cartItem.id }

          if (goodsItem != null) {
            GoodsItemInCartWidget(
              modifier = Modifier.fillParentMaxWidth(),
              index = index,
              goodsItemInCart = cartItem,
              goodsItem = goodsItem,
              onDelete = {
                deleteCartById(
                  id = cartItem.id,
                  transactionTypeIndex = context.transactionTypeIndex,
                  clientId = context.clientId
                )
              },
              increaseQuantityAction = {
                changeCartQuantity(
                  id = cartItem.id,
                  transactionTypeIndex = context.transactionTypeIndex,
                  clientId = context.clientId,
                  current = cartItem.quantity,
                  deltaSteps = 1
                )
              },
              decreaseQuantityAction = {
                changeCartQuantity(
                  id = cartItem.id,
                  transactionTypeIndex = context.transactionTypeIndex,
                  clientId = context.clientId,
                  current = cartItem.quantity,
                  deltaSteps = -1
                )
              }
            )
          }
        }
      }
    }

    val currentTransactionScreens by Navigation
      .getCurrentTransactionScreens(
        context.transactionTypeIndex,
        context.clientId,
        stateValues.isNarrowScreen
      )
      .collectAsState()

    val lastScreen = currentTransactionScreens.lastOrNull()

    if (
      goodsInCart.isNotEmpty() &&
      (
          lastScreen is NavigationScreenModel.Transaction.Cart ||
              lastScreen is NavigationScreenModel.Transaction.Selection
          )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
      ) {
        Spacer(modifier = Modifier.height(4.dp))

        actionButton(
          text = stateValues.stringPayment,
          enabled = stateValues.latestNotification == null,
          onClick = {
            coroutineScope.launch {
              when (context.transactionTypeIndex) {
                0 -> Navigation.TransactionSale.go(NavigationScreenModel.Transaction.Payment)
                1 -> Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.Payment)
                else -> Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.Payment)
              }
            }
          }
        )

        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }
}

//@Composable
//fun AppConfiguration.TransactionCartScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainSale -> {
//        0
//      }
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        1
//      }
//      else -> {
//        2
//      }
//    }
//
//    val clientId = when(transactionTypeIndex) {
//      0 -> {
//        stateValues.navigationTransactionSaleClientId
//      }
//      1 -> {
//        stateValues.navigationTransactionReturnClientId
//      }
//      else -> {
//        stateValues.navigationTransactionSupplyClientId
//      }
//    }
//
//    ScreenAppBarWidget(
//      title = when (transactionTypeIndex) {
//        1 -> stateValues.stringReturn
//        2 -> stateValues.stringSupply
//        else -> stateValues.stringSale
//      },
//      iconPath = when (transactionTypeIndex) {
//        1 -> stateValues.drawablePathIconTransactionReturn
//        2 -> stateValues.drawablePathIconTransactionSupply
//        else -> stateValues.drawablePathIconTransactionSale
//      }
//    )
//
//    val goodsInCart by getCartState(transactionTypeIndex, clientId).collectAsState()
//
//    if (goodsInCart.isEmpty()) {
//      MessageText(
//        modifier = Modifier
//          .fillMaxSize(),
//        stateValues.stringCartEmpty
//      )
//    } else {
//      LazyColumn(
//        modifier = Modifier
//          .weight(1f)
//          .padding(stateValues.marginTextField),
//      ) {
//        itemsIndexed(goodsInCart) { index, item ->
//          stateValues.stock?.find {
//            item.id == it.id
//          }?.let {
//            GoodsItemInCartWidget(
//              index = index,
//              goodsItemInCart = item,
//              goodsItem = it,
//              onDelete = {
//                deleteCartById(item.id, transactionTypeIndex, clientId)
//              },
//              increaseQuantityAction = {
//                  upsertCart(
//                    id = it.id,
//                    transactionTypeIndex,
//                    clientId,
//                    QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
//                  )
//              },
//              decreaseQuantityAction = {
//                upsertCart(
//                  id = it.id,
//                  transactionTypeIndex,
//                  clientId,
//                  QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
//                )
//              }
//            )
//          }
//        }
//      }
//    }
//
//    val currentTransactionScreens by Navigation.getCurrentTransactionScreens(transactionTypeIndex, clientId, stateValues.isNarrowScreen).collectAsState()
//
//    if (goodsInCart.isNotEmpty() && currentTransactionScreens.run { last() is NavigationScreenModel.Transaction.Cart || last() is NavigationScreenModel.Transaction.Selection })
//      Column(
//        modifier = Modifier
//          .fillMaxWidth()
//          .padding(horizontal = 8.dp)
//      ) {
//        Spacer(modifier = Modifier.height(4.dp))
//
//        actionButton(
//          text = stateValues.stringPayment,
//          enabled = stateValues.latestNotification == null,
//          onClick = {
//            coroutineScope.launch {
//              when (transactionTypeIndex) {
//                0 -> {
//                  Navigation.TransactionSale.go(NavigationScreenModel.Transaction.Payment)
//                }
//                1 -> {
//                  Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.Payment)
//                }
//                else -> {
//                  Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.Payment)
//                }
//
//              }
//            }
//          }
//        )
//
//        Spacer(modifier = Modifier.height(4.dp))
//      }
//  }
//}

@Composable
fun AppConfiguration.tabRowWidget(
  modifier: Modifier = Modifier,
  tabs: List<TabContent>,

  selectedIndexInitial: String = tabs.first().id,
  selectedContainerColor: Color = stateValues.AccentColor,
  unselectedContainerColor: Color = Color.Transparent,

  selectedTextColor: Color = stateValues.AccentTextColor,
  unselectedTextColor: Color = stateValues.TextColor,

  cornerRadius: Dp = stateValues.cornerRadius,

  textSize: TextUnit = stateValues.textSize,

  titleText: String = "",
  titleTextSize: TextUnit = stateValues.accentTextSize,
  titleTextColor: Color = stateValues.TextColor,
): TabRowContent {
  var selectedId by rememberSaveable {
    mutableStateOf(selectedIndexInitial)
  }

  LaunchedEffect(selectedIndexInitial) {
    selectedId = selectedIndexInitial
  }

  if (tabs.isNotEmpty()) {
    Column(modifier = modifier) {
      titleText.takeIf { it.isNotEmpty() && it.isNotBlank() }?.apply {
        Text(
          text = this,
          modifier = Modifier,
          style = TextStyle(
            color = titleTextColor,
            fontSize = titleTextSize,
            fontWeight = FontWeight.Bold
          )
        )
      }

      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(cornerRadius))
      ) {
        tabs.forEachIndexed { _, tab ->
          val isSelected = tab.id == selectedId

          val containerColor by animateColorAsState(
            targetValue = if (isSelected) selectedContainerColor else unselectedContainerColor
          )
          val textColor by animateColorAsState(
            targetValue = if (isSelected) selectedTextColor else unselectedTextColor
          )

          Box(
            modifier = Modifier
              .weight(1f)
              .background(containerColor)
              .clickable(
                interactionSource = remember {
                  MutableInteractionSource()
                },
                indication = ripple(color = textColor)
              ) {
                selectedId = tab.id

                tab.onClick?.invoke(tab.id)
              },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = tab.text,
              modifier = Modifier
                .padding(6.dp),
              fontSize = textSize,
              color = textColor,
              textAlign = TextAlign.Center,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }

  return TabRowContent(selectedId)
}

data class TabRowContent(
  var id: String
)

class TabContent(
  val id: String,
  val text: String,
  val onClick: ((String) -> Unit)? = null
)

@Composable
fun AppConfiguration.StoreWidget(
  modifier: Modifier = Modifier,
  store: StoreDataModel,
  textColor: Color = stateValues.TextColor,
  onDelete: ((StoreDataModel) -> Unit)? = null,
  onEdit: ((StoreDataModel) -> Unit)? = null,
  onSetActive: ((StoreDataModel) -> Unit)? = null,
  onSetInactive: ((StoreDataModel) -> Unit)? = null
) {
  Row(
    modifier
      .padding(bottom = 4.dp)
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        if (onSetActive == null) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
        if (onSetActive == null) stateValues.OkayColor else stateValues.PlaceholderTextColor,
        RoundedCornerShape(
          stateValues.cornerRadius
        )
      )
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(top = 8.dp, start = (if (onSetActive == null) 8 else 16).dp, end = 16.dp, bottom = 12.dp),
    ) {
      if (onSetActive == null)
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          actionButton(
            text = "",
            modifier = Modifier.padding(8.dp),
            enabled = false,
            disabledColor = stateValues.OkayColor,
            iconPath = stateValues.drawablePathIconCheck,
            iconContentDescription = stateValues.stringSelect,
          ) {

          }

          Text(
            text = stateValues.stringActiveStore,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold,
            color = stateValues.OkayColor
          )
        }

      Column(
        modifier = Modifier
          .padding(start = 8.dp, end = 8.dp),
      ) {
        store.name.extractLocalizedString(stateValues.appLanguage)?.run {
          Text(
            text = this,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold,
            color = textColor
          )
        }

        store.alias.takeIf { it.any { item -> item.value.isNotEmpty() && item.value.isNotBlank() } }
          ?.extractLocalizedString(stateValues.appLanguage)?.let {
            Text(
              text = it,
              fontSize = stateValues.textSize,
              color = textColor
            )
          }

        store.description.takeIf { it.any { item -> item.value.isNotEmpty() && item.value.isNotBlank() } }
          ?.extractLocalizedString(stateValues.appLanguage)?.let {
            Text(
              text = it,
              fontSize = stateValues.textSize,
              color = textColor
            )
          }

        val companyFormsText = store.companyForms.takeIf { it.isNotEmpty() }?.let { companyForms ->
          StringBuilder()
            .also {
              companyForms.forEachIndexed { index, companyForm ->
                val name = companyForm.name.extractLocalizedString(stateValues.appLanguage)

                name?.run {
                  if (index == companyForms.lastIndex)
                    it.append(name)
                  else
                    it.append("$name, ")
                }
              }
            }
            .toString()
        }

        Text(
          text = companyFormsText.takeIf { it?.isNotEmpty() == true } ?: "No company form specified",
          fontSize = stateValues.textSize,
          fontWeight = FontWeight.Bold,
          color = textColor
        )

        Text(
          text = store.location.name,
          fontSize = stateValues.textSize,
          fontWeight = FontWeight.Bold,
          color = textColor
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextField)
        )
      }
    }

    Column(
      modifier = Modifier
        .padding(end = 16.dp, top = 16.dp, start = 8.dp, bottom = 16.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      onDelete?.let {
        actionButton(
          text = "",
          enabledColor = stateValues.ErrorColor,
          iconPath = stateValues.drawablePathIconDelete,
          iconContentDescription = stateValues.drawablePathIconDelete,
        ) {
          onDelete(store)
        }
      }

      Spacer(
        modifier = Modifier
          .height(stateValues.marginTextField)
      )

      onEdit?.let {
        actionButton(
          text = "",
          iconPath = stateValues.drawablePathIconEdit,
          iconContentDescription = stateValues.drawablePathIconEdit,
        ) {
          onEdit(store)
        }
      }

      Spacer(
        modifier = Modifier
          .height(stateValues.marginTextField)
      )

      onSetActive?.let {
        actionButton(
          text = "",
          enabledColor = stateValues.OkayColor,
          iconPath = stateValues.drawablePathIconCheck,
          iconContentDescription = stateValues.stringSelect,
        ) {
          onSetActive(store)
        }
      } ?: onSetInactive?.let {
        actionButton(
          text = "",
          enabledColor = stateValues.ErrorColor,
          iconPath = stateValues.drawablePathIconCancel,
          iconContentDescription = stateValues.stringMakeInactive
        ) {
          onSetInactive(store)
        }
      }
    }
  }
}

@Composable
fun AppConfiguration.StockWarehouseScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringStock,
      iconPath = stateValues.drawablePathIconStock
    )

    StockWarehouseScreenContent(
      modifier = Modifier
        .weight(1f),
      onDelete = {
        deleteGoodsItem(id = it.id, storeId = stateValues.activeStoreId!!) {

        }
      },
      onEdit = {
        coroutineScope.launch {
          NavigationScreenModel.Stock.AddEditGoodsItem.setState(
            NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID to it.id
          )
          Navigation.Stock.go(NavigationScreenModel.Stock.AddEditGoodsItem, forceSecond = true)
        }
      }
    )
  }
}


@Composable
fun AppConfiguration.StockWarehouseScreenContent(
  modifier: Modifier = Modifier,
  searchQuery: String? = null,
  disableIfOutOfStock: Boolean = false,
  showStockType: Boolean = true,
  onFilter: ((GoodsItemDataModel) -> Boolean)? = null,
  onClick: ((GoodsItemDataModel) -> Unit)? = null,
  onDelete: ((GoodsItemDataModel) -> Unit)? = null,
  onEdit: ((GoodsItemDataModel) -> Unit)? = null,
  onExactSearchHit: ((GoodsItemDataModel) -> Unit)? = null
){
  when (val state = stateValues.stockState) {
    is DataState.Success -> {
      if (state.payload.run { onFilter?.let { filter { onFilter(it) } } ?: this }.isEmpty()) {
        MessageText(
          modifier = modifier
            .fillMaxWidth(),
          stateValues.stringListEmpty
        )
      } else {
        var lSearchQuery: String by rememberSaveable {
          mutableStateOf("")
        }

        if (searchQuery == null) {
          val searchTextFieldContent =
            searchTextField(
              modifier = Modifier
                .padding(start = 8.dp, top = 8.dp, end = 8.dp),
              stateHost = NavigationScreenModel.Stock.Warehouse,
              stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
              barcodeCamScanner = true
            )

          LaunchedEffect(searchTextFieldContent.value) {
            lSearchQuery = searchTextFieldContent.value.text
          }
        } else {
          LaunchedEffect(searchQuery) {
            lSearchQuery = searchQuery
          }
        }

        val items = lSearchQuery
          .takeIf {
            it.isNotEmpty()
          }?.let { query ->
            state.payload
              .run { onFilter?.let { filter { onFilter(it) } } ?: this }
              .search<GoodsItemDataModel>(query)
              .apply {
                if (second && onExactSearchHit != null && first.isNotEmpty())
                  onExactSearchHit(first.first())
              }.first
          } ?: state.payload.run { onFilter?.let { filter { onFilter(it) } } ?: this }

        if (items.isEmpty()) {
          MessageText(
            modifier = modifier
              .fillMaxWidth(),
            stateValues.stringNoMatches
          )
        } else {
          LazyColumn(
            modifier = modifier
              .fillMaxWidth()
              .padding(8.dp)
          ) {
            items(items) { item ->
              GoodsItemInStockWidget(
                modifier = Modifier
                  .alpha(if (disableIfOutOfStock /* TODO && item.quantity.total == 0.0 */) 0.5f else 1f),
                goodsItem = item,
                batches = stateValues.stockBatches.orEmpty().filter { it.goodsItemId == item.id && it.isActive },
                onDelete = onDelete,
                onClick = onClick,
                onEdit = onEdit
              )
            }
          }
        }
      }
    }

    is DataState.Empty -> {
      MessageText(
        modifier = modifier
          .fillMaxWidth(),
        stateValues.stringListEmpty
      )
    }
  }
}


@Composable
fun AppConfiguration.StockScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (stateValues.activeStoreId == null) {
      Column(
        modifier = Modifier
          .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        MessageText(
          text = stateValues.stringNoActiveStore,
          textSize = stateValues.titleTextSize
        )

        actionButton(
          text = stateValues.stringSelectInMenu,
          fillMaxWidthIfTextPresent = false
        ) {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.Stores)
            Navigation.goMain(NavigationScreenModel.Menu.Main)
          }
        }
      }
    } else {
      if (stateValues.isNarrowScreen) {
        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = stateValues.navigationScreensStockLeft.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Stock.Warehouse -> {
              StockWarehouseScreen()
            }
            is NavigationScreenModel.Stock.AddEditGoodsItem -> {
              StockAddEditGoodsItemScreen()
            }
            else -> { }
          }
        }
      } else {
        Row(
          modifier = Modifier
            .weight(1f)
        ) {
          AnimatedContent(
            modifier = Modifier
              .weight(1f),
            targetState = stateValues.navigationScreensStockLeft.last()
          ) { model ->
            when (model) {
              is NavigationScreenModel.Stock.Warehouse -> {
                StockWarehouseScreen()
              }
              is NavigationScreenModel.Stock.AddEditGoodsItem -> {
                StockAddEditGoodsItemScreen()
              }
              else -> { }
            }
          }

          AnimatedContent(
            modifier = Modifier
              .weight(1f),
            targetState = stateValues.navigationScreensStockRight.last()
          ) { model ->
            when (model) {
              is NavigationScreenModel.Stock.Warehouse -> {
                StockWarehouseScreen()
              }
              is NavigationScreenModel.Stock.AddEditGoodsItem -> {
                StockAddEditGoodsItemScreen()
              }
              else -> { }
            }
          }
        }
      }
    }
  }
}


@Composable
fun AppConfiguration.StockBatchWidget(
  modifier: Modifier = Modifier,
  stateHost: StateHost? = null,
  stateKey: String? = null,
  containedSupplierIds: List<String> = emptyList()
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(width = stateValues.unfocusedBorderWidth, color = stateValues.TextColor, shape = RoundedCornerShape(stateValues.cornerRadius))
  ) {
    val supplierContent = stateValues.suppliers?.filter { !containedSupplierIds.contains(it.id) }?.run {
      dropdownListWidget(
        modifier = Modifier
          .padding(16.dp),
        titleText = stateValues.stringSupplier,
        domains = map {
          SelectableDomain(
            id = it.id,
            displayId = it.name,
            name = it.name,
            iconPath = null,
            iconRes = null,
          )
        },
        showName = false,
        search = Triple("search", stateHost, stateKey)
      )


      Spacer(modifier = Modifier.height(4.dp))

      val priceOnFilterValue = { text: String, _: String, _: String? ->
        text.isNumericalDoubleString()
      }
      val priceOnContentValidityCheck = { text: String, id: String, _: String? ->
        text.isNotEmpty() && id.isNumericalDoubleString()
      }

      Spacer(
        modifier = Modifier
          .height(stateValues.marginTextFieldGroup)
      )
    }
  }
}

@Composable
fun AppConfiguration.SplashScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    val imageRes by stateValues.drawableResAITALogo.collectAsState()

    LargeIconWithTitleWidget(
      modifier = Modifier
        .width(stateValues.boundWidgetWidth),
      imageUrl = stateValues.drawablePathAITALogo,
      imageRes = imageRes,
      title = stateValues.stringLogIn
    )
  }
}

data class BatchPriceInfo(
  val supplierId: String,
  val supplyPrice: String,
  val salePrice: String,
  val returnPrice: String,
  val currency: String
)

data class StockAddEditDraft(
  val id: String = "",
  val barcodes: List<String> = listOf(""),
  val name: List<LocalizedStringDataModel> = emptyList(),
  val description: List<LocalizedStringDataModel> = emptyList(),
  val measurementUnitId: String = "0",
  val categoryIds: List<String> = emptyList(),
  val salePrices: List<PriceDataModel> = emptyList(),
  val returnPrices: List<PriceDataModel> = emptyList(),
  val supplyPrices: List<PriceDataModel> = emptyList(),
  val isQuickItem: Boolean = false,
  val note: String = ""
)

fun GoodsItemDataModel.toStockAddEditDraft(): StockAddEditDraft {
  return StockAddEditDraft(
    id = id,
    barcodes = barcodes.ifEmpty { listOf("") },
    name = name,
    description = description,
    measurementUnitId = measurementUnitId,
    categoryIds = categoryIds,
    salePrices = salePrices,
    returnPrices = returnPrices,
    supplyPrices = supplyPrices,
    isQuickItem = isQuickItem,
    note = note.orEmpty()
  )
}

fun StockAddEditDraft.toGoodsItem(
  storeId: String,
  current: GoodsItemDataModel? = null
): GoodsItemDataModel {
  val now = getCurrentTimeMillis()

  return GoodsItemDataModel(
    id = id,
    userId = current?.userId.orEmpty(),
    storeId = storeId,
    barcodes = barcodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    name = name.filter { it.value.isNotBlank() },
    description = description.filter { it.value.isNotBlank() },
    measurementUnitId = measurementUnitId,
    categoryIds = categoryIds,
    salePrices = salePrices,
    returnPrices = returnPrices,
    supplyPrices = supplyPrices,
    isQuickItem = isQuickItem,
    imagePaths = current?.imagePaths.orEmpty(),
    activeShelfBatchId = current?.activeShelfBatchId,
    note = note.takeIf { it.isNotBlank() },
    createdAtMillis = current?.createdAtMillis ?: now,
    updatedAtMillis = now,
    isActive = true
  )
}

fun StockAddEditDraft.isValidStockDraft(): Boolean {
  val cleanBarcodes = barcodes.map { it.trim() }.filter { it.isNotEmpty() }

  val hasName = name.any { it.value.isNotBlank() }
  val hasBarcode = cleanBarcodes.isNotEmpty()
  val hasUnit = measurementUnitId.isNotBlank()
  val hasSalePrice = salePrices.any { it.price.toDoubleOrNull()?.let { price -> price >= 0.0 } == true }
  val hasSupplyPrice = supplyPrices.any { it.price.toDoubleOrNull()?.let { price -> price >= 0.0 } == true }

  return hasName && hasBarcode && hasUnit && hasSalePrice && hasSupplyPrice
}

fun List<GoodsBatchDataModel>.bestBatchForSale(
  goodsItem: GoodsItemDataModel
): GoodsBatchDataModel? {
  val active = firstOrNull {
    it.id == goodsItem.activeShelfBatchId &&
        it.isActive &&
        it.quantity.total > 0.0
  }

  if (active != null) return active

  return filter {
    it.goodsItemId == goodsItem.id &&
        it.isActive &&
        it.quantity.total > 0.0 &&
        it.status != StockBatchStatusDataModel.SoldOut &&
        it.status != StockBatchStatusDataModel.Deleted
  }
    .sortedWith(
      compareBy<GoodsBatchDataModel> {
        it.expirationDateMillis ?: Long.MAX_VALUE
      }.thenByDescending {
        it.shelfPriority
      }
    )
    .firstOrNull()
}

private data class GoodsBatchDraft(
  val id: String = "",
  val goodsItemId: String,
  val storeId: String,
  val supplierId: String? = null,
  val quantityText: String = "1",
  val quantityUnitId: String,
  val supplyPrice: PriceDataModel,
  val salePriceOverride: PriceDataModel? = null,
  val returnPriceOverride: PriceDataModel? = null,
  val expirationDateMillisText: String = "",
  val manufacturedAtMillisText: String = "",
  val shelfPosition: String = "",
  val shelfPriority: String = "0",
  val additionalNotes: String = "",
  val status: StockBatchStatusDataModel = StockBatchStatusDataModel.Delivered
)

private fun GoodsBatchDataModel.toDraft(
  fallbackUnitId: String
): GoodsBatchDraft {
  return GoodsBatchDraft(
    id = id,
    goodsItemId = goodsItemId,
    storeId = storeId,
    supplierId = supplierId,
    quantityText = quantity.total.toString(),
    quantityUnitId = quantity.id.ifBlank { fallbackUnitId },
    supplyPrice = supplyPrice,
    salePriceOverride = salePriceOverride,
    returnPriceOverride = returnPriceOverride,
    expirationDateMillisText = expirationDateMillis?.toString().orEmpty(),
    manufacturedAtMillisText = manufacturedAtMillis?.toString().orEmpty(),
    shelfPosition = shelfPosition.orEmpty(),
    shelfPriority = shelfPriority.toString(),
    additionalNotes = additionalNotes.orEmpty(),
    status = status
  )
}

@Composable
fun AppConfiguration.StockBatchCard(
  batch: GoodsBatchDataModel,
  activeShelfBatchId: String?,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onSetActiveShelf: () -> Unit
) {
  val supplierName = stateValues.suppliers
    .orEmpty()
    .find { it.id == batch.supplierId }
    ?.name
    ?.extractLocalizedString(stateValues.appLanguage)
    ?: "No supplier"

  val isActiveShelf = batch.id == activeShelfBatchId

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        if (isActiveShelf) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(stateValues.BackgroundColor)
      .padding(stateValues.marginTextFieldGroup)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(
        modifier = Modifier.weight(1f)
      ) {
        Text(
          text = if (isActiveShelf) "Active shelf batch" else "Batch",
          color = if (isActiveShelf) stateValues.AccentColor else stateValues.TextColor,
          fontSize = stateValues.textSize,
          fontWeight = FontWeight.Bold
        )

        Text(
          text = supplierName,
          color = stateValues.PlaceholderTextColor,
          fontSize = stateValues.smallTextSize
        )
      }

      Text(
        text = "${batch.quantity.total} ${
          batch.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty()
        }",
        color = stateValues.TextColor,
        fontSize = stateValues.accentTextSize,
        fontWeight = FontWeight.Bold
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Supply price: ${batch.supplyPrice.price} ${batch.supplyPrice.currency}",
      color = stateValues.TextColor,
      fontSize = stateValues.textSize
    )

    batch.expirationDateMillis?.let {
      Text(
        text = "Expires: $it",
        color = stateValues.PlaceholderTextColor,
        fontSize = stateValues.smallTextSize
      )
    }

    batch.additionalNotes?.takeIf { it.isNotBlank() }?.let {
      Text(
        text = it,
        color = stateValues.PlaceholderTextColor,
        fontSize = stateValues.smallTextSize
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      actionButton(
        modifier = Modifier.weight(1f),
        text = "Edit",
        onClick = onEdit
      )

      actionButton(
        modifier = Modifier.weight(1f),
        text = "Shelf",
        enabled = !isActiveShelf,
        onClick = onSetActiveShelf
      )

      actionButton(
        modifier = Modifier.weight(1f),
        text = "Delete",
        enabledColor = stateValues.ErrorColor,
        onClick = onDelete
      )
    }
  }
}

@Composable
fun AppConfiguration.StockBatchEditor(
  modifier: Modifier = Modifier,
  goodsItem: GoodsItemDataModel,
  existingBatch: GoodsBatchDataModel?,
  onCancel: () -> Unit,
  onSaved: () -> Unit
) {
  val defaultUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
    .find { it.id == goodsItem.measurementUnitId }
    ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.first()

  val defaultCurrency =
    goodsItem.supplyPrices.firstOrNull()?.currency
      ?: goodsItem.salePrices.firstOrNull()?.currency
      ?: "KZT"

  var draft by remember(existingBatch?.id, goodsItem.id) {
    mutableStateOf(
      existingBatch?.toDraft(defaultUnit.id)
        ?: GoodsBatchDraft(
          goodsItemId = goodsItem.id,
          storeId = goodsItem.storeId,
          supplierId = null,
          quantityUnitId = defaultUnit.id,
          supplyPrice = PriceDataModel(
            price = "0",
            currency = defaultCurrency,
            supplierId = ""
          )
        )
    )
  }

  val supplierGoodsPricesPayload by supplierGoodsPricesState.payload.collectAsState()
  val supplierGoodsPrices = supplierGoodsPricesPayload.orEmpty()

  var lastAutoFillKey by remember {
    mutableStateOf("")
  }

  LaunchedEffect(
    draft.goodsItemId,
    draft.supplierId,
    supplierGoodsPrices
  ) {
    val supplierId = draft.supplierId ?: return@LaunchedEffect
    val autoFillKey = "${draft.goodsItemId}:$supplierId"

    if (autoFillKey == lastAutoFillKey)
      return@LaunchedEffect

    val rememberedPrice = supplierGoodsPrices.find {
      it.supplierId == supplierId &&
          it.goodsItemId == draft.goodsItemId &&
          it.isActive
    }

    rememberedPrice?.let {
      draft = draft.copy(
        supplyPrice = it.supplyPrice
      )
    }

    lastAutoFillKey = autoFillKey
  }

  val suppliers = stateValues.suppliers.orEmpty()

  Column(
    modifier = modifier.fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = if (existingBatch == null) "Add batch" else "Edit batch",
      onBack = onCancel
    )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(stateValues.marginTextField)
    ) {
      item {
        SimpleDropdownField(
          title = stateValues.stringSupplier,
          selectedId = draft.supplierId,
          options = suppliers.map {
            DropdownOption(
              id = it.id,
              title = it.name.extractLocalizedString(stateValues.appLanguage)
                ?: it.name.firstOrNull()?.value
                ?: it.id
            )
          },
          placeholder = "Select supplier",
          onSelected = { selectedSupplierId ->
            draft = draft.copy(
              supplierId = selectedSupplierId,
              supplyPrice = draft.supplyPrice.copy(
                supplierId = selectedSupplierId
              )
            )
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        SimpleTextInput(
          modifier = Modifier.fillMaxWidth(),
          value = draft.quantityText,
          placeholder = "Quantity",
          onValueChange = {
            if (it.isEmpty() || it.isNumericalDoubleString()) {
              draft = draft.copy(quantityText = it)
            }
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        SimpleDropdownField(
          title = "Unit",
          selectedId = draft.quantityUnitId,
          options = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map {
            DropdownOption(
              id = it.id,
              title = it.immutableUnitName.extractLocalizedString(stateValues.appLanguage)
                ?: it.id
            )
          },
          placeholder = "Select unit",
          onSelected = {
            draft = draft.copy(quantityUnitId = it)
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        SimpleTextInput(
          modifier = Modifier.fillMaxWidth(),
          value = draft.supplyPrice.price,
          placeholder = stateValues.stringSupplyPrice,
          onValueChange = {
            if (it.isEmpty() || it.isNumericalDoubleString()) {
              draft = draft.copy(
                supplyPrice = draft.supplyPrice.copy(price = it)
              )
            }
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        SimpleTextInput(
          modifier = Modifier.fillMaxWidth(),
          value = draft.expirationDateMillisText,
          placeholder = "Expiration date millis optional",
          onValueChange = {
            if (it.all { char -> char.isDigit() }) {
              draft = draft.copy(expirationDateMillisText = it)
            }
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        SimpleTextInput(
          modifier = Modifier.fillMaxWidth(),
          value = draft.shelfPosition,
          placeholder = "Shelf position optional",
          onValueChange = {
            draft = draft.copy(shelfPosition = it)
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        SimpleTextInput(
          modifier = Modifier.fillMaxWidth(),
          value = draft.shelfPriority,
          placeholder = "Shelf priority",
          onValueChange = {
            if (it.all { char -> char.isDigit() }) {
              draft = draft.copy(shelfPriority = it)
            }
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        SimpleTextInput(
          modifier = Modifier.fillMaxWidth(),
          value = draft.additionalNotes,
          placeholder = "Additional notes optional",
          singleLine = false,
          onValueChange = {
            draft = draft.copy(additionalNotes = it)
          }
        )

        Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      actionButton(
        modifier = Modifier.weight(1f),
        text = stateValues.stringCancel,
        enabledColor = stateValues.DisabledColor,
        onClick = onCancel
      )

      actionButton(
        modifier = Modifier.weight(1f),
        text = stateValues.stringConfirm,
        enabled = draft.quantityText.toDoubleOrNull()?.let { it > 0.0 } == true,
        onClick = {
          val unit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
            .find { it.id == draft.quantityUnitId }
            ?: defaultUnit

          val batch = GoodsBatchDataModel(
            id = draft.id,
            goodsItemId = draft.goodsItemId,
            storeId = draft.storeId,
            supplierId = draft.supplierId,

            quantity = unit.copy(
              total = draft.quantityText.toDoubleOrNull() ?: 0.0
            ),

            supplyPrice = draft.supplyPrice,
            salePriceOverride = draft.salePriceOverride,
            returnPriceOverride = draft.returnPriceOverride,

            expirationDateMillis = draft.expirationDateMillisText.toLongOrNull(),
            manufacturedAtMillis = draft.manufacturedAtMillisText.toLongOrNull(),

            discounts = emptyList(),

            shelfPosition = draft.shelfPosition.takeIf { it.isNotBlank() },
            shelfPriority = draft.shelfPriority.toIntOrNull() ?: 0,

            status = draft.status,
            additionalNotes = draft.additionalNotes.takeIf { it.isNotBlank() },

            isActive = true
          )

          if (existingBatch == null) {
            addGoodsBatches(listOf(batch)) {
              if (it is DataState.Success) {
                onSaved()
              }
            }
          } else {
            updateGoodsBatches(listOf(batch)) {
              if (it is DataState.Success) {
                onSaved()
              }
            }
          }
        }
      )
    }
  }
}

@Composable
fun AppConfiguration.StockAddEditIdentityPage(
  modifier: Modifier = Modifier,
  draft: StockAddEditDraft,
  onDraftChanged: (StockAddEditDraft) -> Unit
) {
  LazyColumn(
    modifier = modifier.padding(stateValues.marginTextField)
  ) {
    item {
      BarcodeListEditor(
        title = stateValues.stringBarcode,
        barcodes = draft.barcodes,
        onChanged = { onDraftChanged(draft.copy(barcodes = it)) }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      LocalizedStringListEditor(
        title = stateValues.stringName,
        values = draft.name,
        onChanged = { onDraftChanged(draft.copy(name = it)) }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      LocalizedStringListEditor(
        title = "Description",
        values = draft.description,
        onChanged = { onDraftChanged(draft.copy(description = it)) }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      SimpleDropdownField(
        title = "Measurement unit",
        selectedId = draft.measurementUnitId,
        options = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map {
          DropdownOption(
            id = it.id,
            title = it.immutableUnitName.extractLocalizedString(stateValues.appLanguage)
              ?: it.id
          )
        },
        placeholder = "Select unit",
        onSelected = {
          onDraftChanged(draft.copy(measurementUnitId = it))
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      SimpleDropdownField(
        title = stateValues.stringCategory,
        selectedId = draft.categoryIds.firstOrNull(),
        options = stateValues.goodsCategories.orEmpty().map {
          DropdownOption(
            id = it.id,
            title = it.name.visibleLocalizedString(stateValues.appLanguage, it.id)
          )
        },
        placeholder = "Select category",
        onSelected = {
          onDraftChanged(draft.copy(categoryIds = listOf(it)))
        }
      )
    }
  }
}

@Composable
fun AppConfiguration.StockAddEditBatchesPage(
  modifier: Modifier = Modifier,
  goodsItem: GoodsItemDataModel?
) {
  if (goodsItem == null || goodsItem.id.isBlank()) {
    Box(
      modifier = modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      MessageText(
        text = "Save the goods item first, then you can add batches."
      )
    }

    return
  }

  var editingBatch by remember {
    mutableStateOf<GoodsBatchDataModel?>(null)
  }

  var addingBatch by remember {
    mutableStateOf(false)
  }

  val batches = stateValues.stockBatches
    .orEmpty()
    .filter {
      it.goodsItemId == goodsItem.id && it.isActive
    }
    .sortedWith(
      compareBy<GoodsBatchDataModel> {
        it.expirationDateMillis ?: Long.MAX_VALUE
      }.thenByDescending {
        it.shelfPriority
      }
    )

  if (addingBatch || editingBatch != null) {
    StockBatchEditor(
      modifier = modifier,
      goodsItem = goodsItem,
      existingBatch = editingBatch,
      onCancel = {
        addingBatch = false
        editingBatch = null
      },
      onSaved = {
        addingBatch = false
        editingBatch = null
      }
    )

    return
  }

  Column(
    modifier = modifier.fillMaxSize()
  ) {
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(stateValues.marginTextField)
    ) {
      item {
        Text(
          text = "Batches",
          color = stateValues.TextColor,
          fontSize = stateValues.titleTextSize,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
      }

      if (batches.isEmpty()) {
        item {
          MessageText(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = stateValues.marginTextFieldGroup),
            text = "No batches yet"
          )
        }
      } else {
        items(batches) { batch ->
          StockBatchCard(
            batch = batch,
            activeShelfBatchId = goodsItem.activeShelfBatchId,
            onEdit = {
              editingBatch = batch
            },
            onDelete = {
              stateValues.activeStoreId?.let { storeId ->
                deleteGoodsBatches(
                  ids = listOf(batch.id),
                  storeId = storeId,
                  onCompleted = null
                )
              }
            },
            onSetActiveShelf = {
              stateValues.activeStoreId?.let { storeId ->
                setActiveShelfBatch(batch, storeId)
              }
            }
          )

          Spacer(modifier = Modifier.height(stateValues.marginTextField))
        }
      }

      item {
        Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
      }
    }

    actionButton(
      modifier = Modifier.padding(8.dp),
      text = "Add batch",
      onClick = {
        addingBatch = true
      }
    )
  }
}

@Composable
fun AppConfiguration.StockSupplierPricesPage(
  modifier: Modifier = Modifier,
  goodsItem: GoodsItemDataModel?
) {
  if (goodsItem == null || goodsItem.id.isBlank()) {
    Box(
      modifier = modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      MessageText(
        text = "Save the goods item first, then supplier prices will appear."
      )
    }

    return
  }

  val supplierPricesPayload by supplierGoodsPricesState.payload.collectAsState()
  val supplierPrices = supplierPricesPayload
    .orEmpty()
    .filter {
      it.goodsItemId == goodsItem.id && it.isActive
    }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(stateValues.marginTextField)
  ) {
    item {
      Text(
        text = "Supplier prices",
        color = stateValues.TextColor,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
    }

    if (supplierPrices.isEmpty()) {
      item {
        MessageText(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = stateValues.marginTextFieldGroup),
          text = "No supplier prices yet. They will be remembered automatically when you add batches."
        )
      }
    } else {
      items(supplierPrices) { supplierPrice ->
        val supplierName = stateValues.suppliers
          .orEmpty()
          .find { it.id == supplierPrice.supplierId }
          ?.name
          ?.extractLocalizedString(stateValues.appLanguage)
          ?: supplierPrice.supplierId

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(
              stateValues.unfocusedBorderWidth,
              stateValues.PlaceholderTextColor,
              RoundedCornerShape(stateValues.cornerRadius)
            )
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup)
        ) {
          Text(
            text = supplierName,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "${supplierPrice.supplyPrice.price} ${supplierPrice.supplyPrice.currency}",
            color = stateValues.AccentColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
          )

          supplierPrice.lastUsedAtMillis?.let {
            Text(
              text = "Last used: $it",
              color = stateValues.PlaceholderTextColor,
              fontSize = stateValues.smallTextSize
            )
          }
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextField))
      }
    }

    item {
      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
fun AppConfiguration.StockAddEditNotesPage(
  modifier: Modifier = Modifier,
  draft: StockAddEditDraft,
  onDraftChanged: (StockAddEditDraft) -> Unit
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(stateValues.marginTextField)
  ) {
    item {
      Text(
        text = "Notes",
        color = stateValues.TextColor,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      SimpleTextInput(
        modifier = Modifier.fillMaxWidth(),
        value = draft.note,
        placeholder = "Additional notes about this goods item",
        singleLine = false,
        onValueChange = {
          onDraftChanged(
            draft.copy(note = it)
          )
        }
      )

      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
fun AppConfiguration.StockAddEditPricesPage(
  modifier: Modifier = Modifier,
  draft: StockAddEditDraft,
  onDraftChanged: (StockAddEditDraft) -> Unit
) {
  val defaultCurrency = stateValues.globalAppConfiguration
    .countries
    .firstOrNull()
    ?.currencies
    ?.firstOrNull()
    ?.code
    ?: "KZT"

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(stateValues.marginTextField)
  ) {
    item {
      Text(
        text = "Prices",
        color = stateValues.TextColor,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      StockSinglePriceEditor(
        title = stateValues.stringSalePrice,
        price = draft.salePrices.firstOrNull()
          ?: PriceDataModel(
            price = "",
            currency = defaultCurrency,
            supplierId = ""
          ),
        onChanged = {
          onDraftChanged(
            draft.copy(
              salePrices = listOf(it)
            )
          )
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      StockSinglePriceEditor(
        title = stateValues.stringReturnPrice,
        price = draft.returnPrices.firstOrNull()
          ?: PriceDataModel(
            price = "",
            currency = defaultCurrency,
            supplierId = ""
          ),
        onChanged = {
          onDraftChanged(
            draft.copy(
              returnPrices = listOf(it)
            )
          )
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      StockSinglePriceEditor(
        title = stateValues.stringSupplyPrice,
        price = draft.supplyPrices.firstOrNull()
          ?: PriceDataModel(
            price = "",
            currency = defaultCurrency,
            supplierId = ""
          ),
        onChanged = {
          onDraftChanged(
            draft.copy(
              supplyPrices = listOf(it)
            )
          )
        }
      )

      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
fun AppConfiguration.StockSinglePriceEditor(
  title: String,
  price: PriceDataModel,
  onChanged: (PriceDataModel) -> Unit
) {
  Column {
    Text(
      text = title,
      color = stateValues.TextColor,
      fontSize = stateValues.textSize,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    SimpleTextInput(
      modifier = Modifier.fillMaxWidth(),
      value = price.price,
      placeholder = "0",
      onValueChange = {
        if (it.isEmpty() || it.isNumericalDoubleString()) {
          onChanged(
            price.copy(price = it)
          )
        }
      }
    )

    Spacer(modifier = Modifier.height(8.dp))

    SimpleDropdownField(
      title = "Currency",
      selectedId = price.currency,
      options = stateValues.globalAppConfiguration
        .countries
        .flatMap { it.currencies }
        .distinctBy { it.code }
        .map {
          DropdownOption(
            id = it.code,
            title = "${it.code} ${it.symbol}"
          )
        },
      placeholder = "Select currency",
      onSelected = {
        onChanged(
          price.copy(currency = it)
        )
      }
    )
  }
}

private fun AppConfiguration.stockLanguageDomains(): List<SelectableDomain> {
  return stateValues.globalAppConfiguration.languages.map { language ->
    SelectableDomain(
      id = language.language,
      displayId = language.language.uppercase().toLocalizedSingleMain(),
      name = language.name,
      iconPath = null,
      iconRes = language.mapIconRes()
    )
  }
}

private fun AppConfiguration.stockCurrencyDomains(): List<SelectableDomain> {
  return stateValues.globalAppConfiguration.countries
    .flatMap { it.currencies }
    .distinctBy { it.code }
    .map { currency ->
      SelectableDomain(
        id = currency.code,
        displayId = currency.code.toLocalizedSingleMain(),
        name = currency.symbol.toLocalizedSingleMain(),
        iconPath = null,
        iconRes = null
      )
    }
}

private fun AppConfiguration.stockQuantityUnitDomains(): List<SelectableDomain> {
  return stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map { unit ->
    SelectableDomain(
      id = unit.id,
      displayId = unit.immutableUnitName,
      name = unit.immutableUnitName,
      iconPath = null,
      iconRes = null
    )
  }
}

private fun AppConfiguration.stockCategoryDomains(): List<SelectableDomain> {
  return stateValues.goodsCategories.orEmpty().map { category ->
    val localizedName = category.name
      .visibleLocalizedString(stateValues.appLanguage, category.id)
      .toLocalizedSingleMain()

    SelectableDomain(
      id = category.id,
      displayId = localizedName,
      name = localizedName,
      iconPath = null,
      iconRes = null
    )
  }
}

private fun List<LocalizedStringDataModel>.toDomainSelectionItems(
  fallbackLanguageId: String
): List<DomainSelectionTextFieldGroupItemContent> {
  val cleaned = map { it.copy(value = it.value.trim()) }
    .filter { it.language.isNotBlank() || it.value.isNotBlank() }
    .distinctBy { it.language }

  return cleaned.ifEmpty {
    listOf(LocalizedStringDataModel(fallbackLanguageId, ""))
  }.map {
    DomainSelectionTextFieldGroupItemContent(
      value = TextFieldValue(it.value, selection = TextRange(it.value.length)),
      selectedDomainId = "text",
      selectedSecondaryDomainId = it.language.ifBlank { fallbackLanguageId },
      isContentValid = true
    )
  }
}

private fun List<DomainSelectionTextFieldGroupItemContent>.toLocalizedStringsFromLanguageSelection(): List<LocalizedStringDataModel> {
  return map {
    LocalizedStringDataModel(
      language = it.selectedSecondaryDomainId,
      value = it.value.text.trim()
    )
  }
    .filter { it.language.isNotBlank() && it.value.isNotBlank() }
    .distinctBy { it.language }
}

private fun AppConfiguration.emptyLocalizedItemForCurrentLanguage(): List<LocalizedStringDataModel> {
  val language = stateValues.appLanguage.takeIf { it.isNotBlank() }
    ?: stateValues.globalAppConfiguration.languages.firstOrNull()?.language
    ?: "main"

  return listOf(LocalizedStringDataModel(language, ""))
}

private fun AppConfiguration.stockTextOnlyDomain(): List<SelectableDomain> {
  return listOf(
    SelectableDomain(
      id = "text",
      displayId = "".toLocalizedSingleMain(),
      name = null,
      iconPath = null,
      iconRes = null
    )
  )
}

@Composable
private fun AppConfiguration.StockLocalizedStringGroupEditor(
  title: String,
  placeholder: String,
  values: List<LocalizedStringDataModel>,
  addText: String,
  required: Boolean,
  onChanged: (List<LocalizedStringDataModel>) -> Unit
) {
  val fallbackLanguageId = stateValues.appLanguage.takeIf { it.isNotBlank() }
    ?: stateValues.globalAppConfiguration.languages.firstOrNull()?.language
    ?: "main"

  val languageDomains = stockLanguageDomains()
  val textOnlyDomain = stockTextOnlyDomain()

  var focusTargetIndex by rememberSaveable {
    mutableStateOf(0)
  }

  var data by rememberSaveable {
    mutableStateOf(values.toDomainSelectionItems(fallbackLanguageId))
  }

  LaunchedEffect(values, fallbackLanguageId) {
    val next = values.toDomainSelectionItems(fallbackLanguageId)
    if (next.map { it.selectedSecondaryDomainId to it.value.text } != data.map { it.selectedSecondaryDomainId to it.value.text }) {
      data = next
    }
  }

  val usedLanguageIds = data.map { it.selectedSecondaryDomainId }.filter { it.isNotBlank() }.toSet()
  val availableLanguageDomains = languageDomains.filter { it.id !in usedLanguageIds }

  Column(modifier = Modifier.fillMaxWidth()) {
    data.forEachIndexed { index, item ->
      val rowLanguageDomains = (
          listOfNotNull(languageDomains.find { it.id == item.selectedSecondaryDomainId }) +
              languageDomains.filter { it.id !in usedLanguageIds || it.id == item.selectedSecondaryDomainId }
          )
        .distinctBy { it.id }

      val instance = domainSelectionTextField(
        titleText = if (index == 0) title else "$title ${index + 1}",
        placeholderText = placeholder,
        valueInitial = item.value.text,
        titleIconButtonPath = if (data.size == 1) null else stateValues.drawablePathIconDelete,
        onTitleIconButtonClick = if (data.size == 1) null else {
          {
            data = data.toMutableList().also { list ->
              if (index in list.indices) list.removeAt(index)
            }.ifEmpty {
              listOf(
                DomainSelectionTextFieldGroupItemContent(
                  value = TextFieldValue(""),
                  selectedDomainId = "text",
                  selectedSecondaryDomainId = fallbackLanguageId,
                  isContentValid = true
                )
              )
            }
            focusTargetIndex = (index - 1).coerceAtLeast(0)
          }
        },
        domains = textOnlyDomain,
        selectedInitial = "text",
        selectionEnabled = false,
        displayFullDomain = false,
        secondaryDomains = rowLanguageDomains,
        selectedSecondaryInitial = item.selectedSecondaryDomainId.takeIf { it.isNotBlank() }
          ?: rowLanguageDomains.firstOrNull()?.id
          ?: fallbackLanguageId,
        secondaryDomainsShowId = true,
        secondaryDomainsShowName = false,
        keyboardType = KeyboardType.Text,
        isFocusedInitial = index == focusTargetIndex && item.value.text.isBlank(),
        contentInvalidText = if (required) placeholder else null,
        onContentValidityCheck = if (required) {
          { value, _, _ -> value.isNotBlank() }
        } else null
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextField))

      LaunchedEffect(instance.value.text, instance.selectedSecondaryId) {
        val selectedLanguageId = instance.selectedSecondaryId ?: item.selectedSecondaryDomainId
        val nextItem = item.copy(
          value = instance.value,
          selectedDomainId = "text",
          selectedSecondaryDomainId = selectedLanguageId,
          isContentValid = instance.isContentValid
        )

        if (data.getOrNull(index) != nextItem) {
          data = data.toMutableList().also { list ->
            if (index in list.indices) list[index] = nextItem
          }
        }
      }
    }

    LaunchedEffect(data) {
      val next = data.toLocalizedStringsFromLanguageSelection()
      val current = values
        .map { it.copy(value = it.value.trim()) }
        .filter { it.language.isNotBlank() && it.value.isNotBlank() }
        .distinctBy { it.language }

      if (next != current) {
        onChanged(next)
      }
    }

    if (availableLanguageDomains.isNotEmpty()) {
      actionButton(
        modifier = Modifier.fillMaxWidth(),
        text = addText,
        iconPath = stateValues.drawablePathIconAdd
      ) {
        val nextLanguageId = availableLanguageDomains.first().id
        focusTargetIndex = data.size
        data = data + DomainSelectionTextFieldGroupItemContent(
          value = TextFieldValue(""),
          selectedDomainId = "text",
          selectedSecondaryDomainId = nextLanguageId,
          isContentValid = true
        )
      }
    }
  }
}


private fun List<PriceDataModel>.toStockPriceEditorItems(
  fallbackCurrency: String
): List<DomainSelectionTextFieldGroupItemContent> {
  val cleaned = filter { it.currency.isNotBlank() || it.price.isNotBlank() }
    .distinctBy { it.currency }

  return cleaned.ifEmpty {
    listOf(PriceDataModel("", fallbackCurrency, ""))
  }.map {
    DomainSelectionTextFieldGroupItemContent(
      value = TextFieldValue(it.price, selection = TextRange(it.price.length)),
      selectedDomainId = "text",
      selectedSecondaryDomainId = it.currency.ifBlank { fallbackCurrency },
      isContentValid = true
    )
  }
}

private fun List<DomainSelectionTextFieldGroupItemContent>.toPriceDataModelsFromCurrencySelection(): List<PriceDataModel> {
  return map {
    PriceDataModel(
      price = it.value.text.trim(),
      currency = it.selectedSecondaryDomainId,
      supplierId = ""
    )
  }
    .filter { it.currency.isNotBlank() && it.price.isNotBlank() }
    .distinctBy { it.currency }
}
@Composable
private fun AppConfiguration.StockPriceGroupEditor(
  title: String,
  placeholder: String,
  prices: List<PriceDataModel>,
  addText: String,
  onChanged: (List<PriceDataModel>) -> Unit
) {
  val currencies = stockCurrencyDomains()
  val fallbackCurrency = currencies.firstOrNull()?.id ?: "KZT"

  var focusTargetIndex by rememberSaveable {
    mutableStateOf(-1)
  }

  var data by remember {
    mutableStateOf(
      prices.toStockPriceEditorItems(fallbackCurrency)
    )
  }

  LaunchedEffect(prices, fallbackCurrency) {
    val next = prices.toStockPriceEditorItems(fallbackCurrency)
    if (next.map { it.selectedSecondaryDomainId to it.value.text } != data.map { it.selectedSecondaryDomainId to it.value.text }) {
      data = next
    }
  }

  val usedCurrencyIds = data.map { it.selectedSecondaryDomainId }.filter { it.isNotBlank() }.toSet()
  val availableCurrencyDomains = currencies.filter { it.id !in usedCurrencyIds }

  Column(modifier = Modifier.fillMaxWidth()) {
    data.forEachIndexed { index, item ->
      val rowCurrencyDomains = (
          listOfNotNull(currencies.find { it.id == item.selectedSecondaryDomainId }) +
              currencies.filter { it.id !in usedCurrencyIds || it.id == item.selectedSecondaryDomainId }
          )
        .distinctBy { it.id }

      val content = domainSelectionTextField(
        titleText = if (index == 0) title else "$title ${index + 1}",
        placeholderText = placeholder,
        valueInitial = item.value.text,
        titleIconButtonPath = if (data.size == 1) null else stateValues.drawablePathIconDelete,
        onTitleIconButtonClick = if (data.size == 1) null else {
          {
            data = data.toMutableList().also { list ->
              if (index in list.indices) list.removeAt(index)
            }.ifEmpty {
              listOf(
                DomainSelectionTextFieldGroupItemContent(
                  value = TextFieldValue(""),
                  selectedDomainId = "text",
                  selectedSecondaryDomainId = fallbackCurrency,
                  isContentValid = true
                )
              )
            }
            focusTargetIndex = (index - 1).coerceAtLeast(0)
          }
        },
        domains = stockTextOnlyDomain(),
        selectedInitial = "text",
        selectionEnabled = false,
        displayFullDomain = false,
        secondaryDomains = rowCurrencyDomains,
        selectedSecondaryInitial = item.selectedSecondaryDomainId.takeIf { it.isNotBlank() }
          ?: rowCurrencyDomains.firstOrNull()?.id
          ?: fallbackCurrency,
        secondaryDomainsShowId = true,
        secondaryDomainsShowName = false,
        keyboardType = KeyboardType.Decimal,
        isFocusedInitial = index == focusTargetIndex,
        contentInvalidText = placeholder,
        onContentValidityCheck = { value, _, _ ->
          value.toDoubleOrNull()?.let { it >= 0.0 } == true
        },
        onFilterValue = { value, _, _ ->
          value.isEmpty() || value.isNumericalDoubleString()
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextField))

      LaunchedEffect(content.value.text, content.selectedSecondaryId) {
        val selectedCurrencyId = content.selectedSecondaryId ?: item.selectedSecondaryDomainId
        val nextItem = item.copy(
          value = content.value,
          selectedDomainId = "text",
          selectedSecondaryDomainId = selectedCurrencyId,
          isContentValid = content.isContentValid
        )

        if (data.getOrNull(index) != nextItem) {
          data = data.toMutableList().also { list ->
            if (index in list.indices) list[index] = nextItem
          }
        }
      }
    }

    LaunchedEffect(data) {
      val next = data.toPriceDataModelsFromCurrencySelection()
      val current = prices
        .filter { it.price.isNotBlank() && it.currency.isNotBlank() }
        .distinctBy { it.currency }

      if (next != current) {
        onChanged(next)
      }
    }

    if (availableCurrencyDomains.isNotEmpty()) {
      actionButton(
        modifier = Modifier.fillMaxWidth(),
        text = addText,
        iconPath = stateValues.drawablePathIconAdd
      ) {
        val nextCurrencyId = availableCurrencyDomains.first().id
        focusTargetIndex = data.size
        data = data + DomainSelectionTextFieldGroupItemContent(
          value = TextFieldValue(""),
          selectedDomainId = "text",
          selectedSecondaryDomainId = nextCurrencyId,
          isContentValid = true
        )
      }
    }
  }
}

private data class StockAddEditTabContent(
  val id: String,
  val title: String,
  val iconPath: String? = null,
  val enabled: Boolean = true
)

@Composable
private fun AppConfiguration.StockAddEditTabs(
  modifier: Modifier = Modifier,
  selectedId: String,
  tabs: List<StockAddEditTabContent>,
  onSelected: (String) -> Unit
) {
  LazyRow(
    modifier = modifier
      .fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
  ) {
    items(tabs) { tab ->
      val selected = selectedId == tab.id

      Row(
        modifier = Modifier
          .height(stateValues.textFieldHeight)
          .clip(RoundedCornerShape(stateValues.cornerRadius))
          .border(
            width = if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
            color = when {
              selected -> stateValues.AccentColor
              tab.enabled -> stateValues.PlaceholderTextColor
              else -> stateValues.DisabledColor
            },
            shape = RoundedCornerShape(stateValues.cornerRadius)
          )
          .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
          .alpha(if (tab.enabled) 1f else 0.55f)
          .clickable(
            enabled = tab.enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = if (selected) stateValues.AccentTextColor else stateValues.TextColor),
            onClick = { onSelected(tab.id) }
          )
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        tab.iconPath?.let {
          CpImage(
            modifier = Modifier
              .size(18.dp),
            url = it,
            fallbackRes = Res.drawable._0_0,
            contentDescription = tab.title,
            tintColor = if (selected) stateValues.AccentTextColor else stateValues.TextColor
          )

          Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
          text = tab.title,
          color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
          fontSize = stateValues.textSize,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun AppConfiguration.StockAddEditInfoTab(
  modifier: Modifier = Modifier,
  draft: StockAddEditDraft,
  onDraftChanged: (StockAddEditDraft) -> Unit
) {
  LazyColumn(
    modifier = modifier
      .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.92f)
      .padding(start = 8.dp, top = 8.dp, end = 8.dp)
  ) {
    item {
      BarcodeListEditor(
        title = stateValues.stringBarcode,
        barcodes = draft.barcodes.ifEmpty { listOf("") },
        onChanged = {
          onDraftChanged(draft.copy(barcodes = it.ifEmpty { listOf("") }))
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      StockLocalizedStringGroupEditor(
        title = stateValues.stringName,
        placeholder = stateValues.stringEnterName,
        values = draft.name,
        addText = stateValues.stringAddTranslation,
        required = true,
        onChanged = {
          onDraftChanged(draft.copy(name = it))
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      StockLocalizedStringGroupEditor(
        title = stateValues.stringDescription,
        placeholder = stateValues.stringEnterDescription,
        values = draft.description,
        addText = "${stateValues.stringAdd} ${stateValues.stringDescription}",
        required = false,
        onChanged = {
          onDraftChanged(draft.copy(description = it))
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      val measurementUnitDropdown = dropdownListWidget(
        titleText = stateValues.stringMeasurementUnit,
        domains = stockQuantityUnitDomains(),
        selectedInitial = draft.measurementUnitId,
        showId = false,
        showName = true
      )

      LaunchedEffect(measurementUnitDropdown.selectedId) {
        if (measurementUnitDropdown.selectedId != draft.measurementUnitId) {
          onDraftChanged(draft.copy(measurementUnitId = measurementUnitDropdown.selectedId))
        }
      }

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      val categoryDomains = stockCategoryDomains()

      if (categoryDomains.isEmpty()) {
        Text(
          text = stateValues.stringCategory,
          color = stateValues.TextColor,
          fontSize = stateValues.accentTextSize,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 4.dp)
        )

        MessageText(
          modifier = Modifier
            .fillMaxWidth()
            .height(stateValues.textFieldHeight),
          text = stateValues.stringListEmpty,
          textSize = stateValues.textSize
        )
      } else {
        val categoryDropdown = dropdownListWidget(
          titleText = stateValues.stringCategory,
          domains = categoryDomains,
          selectedInitial = draft.categoryIds.firstOrNull() ?: categoryDomains.first().id,
          showId = false,
          showName = true,
          search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Stock.AddEditGoodsItem, "stock_category_search")
        )

        LaunchedEffect(categoryDropdown.selectedId) {
          if (draft.categoryIds.firstOrNull() != categoryDropdown.selectedId) {
            onDraftChanged(draft.copy(categoryIds = listOf(categoryDropdown.selectedId)))
          }
        }
      }

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(stateValues.textFieldHeight)
          .clip(RoundedCornerShape(stateValues.cornerRadius))
          .border(
            stateValues.unfocusedBorderWidth,
            if (draft.isQuickItem) stateValues.AccentColor else stateValues.PlaceholderTextColor,
            RoundedCornerShape(stateValues.cornerRadius)
          )
          .background(stateValues.BackgroundColor)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = stateValues.AccentColor),
            onClick = {
              onDraftChanged(draft.copy(isQuickItem = !draft.isQuickItem))
            }
          )
          .padding(horizontal = stateValues.marginTextFieldGroup),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = draft.isQuickItem,
          onCheckedChange = {
            onDraftChanged(draft.copy(isQuickItem = it))
          },
          colors = CheckboxDefaults.colors(
            checkedColor = stateValues.AccentColor,
            uncheckedColor = stateValues.PlaceholderTextColor,
            checkmarkColor = stateValues.AccentTextColor
          )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
          text = stateValues.stringQuick,
          color = stateValues.TextColor,
          fontSize = stateValues.textSize,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      genericTextField(
        titleText = "Notes / ${stateValues.stringOptional}",
        valueInitial = draft.note,
        placeholderText = stateValues.stringOptional,
        wide = true,
        onValueChange = { value, applyChange ->
          applyChange()
          onDraftChanged(draft.copy(note = value))
        }
      )

      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
private fun AppConfiguration.StockAddEditPricesTab(
  modifier: Modifier = Modifier,
  draft: StockAddEditDraft,
  defaultCurrency: String,
  returnPriceManuallyEdited: Boolean,
  onReturnPriceManuallyEditedChanged: (Boolean) -> Unit,
  onDraftChanged: (StockAddEditDraft) -> Unit
) {
  LazyColumn(
    modifier = modifier
      .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.92f)
      .padding(start = 8.dp, top = 8.dp, end = 8.dp)
  ) {
    item {
      StockPriceGroupEditor(
        title = stateValues.stringSalePrice,
        placeholder = stateValues.stringEnterSalePrice,
        prices = draft.salePrices.ifEmpty { listOf(PriceDataModel("", defaultCurrency, "")) },
        addText = "${stateValues.stringAdd} ${stateValues.stringSalePrice}",
        onChanged = { salePrices ->
          val safeSalePrices = salePrices.ifEmpty { listOf(PriceDataModel("", defaultCurrency, "")) }
          val updated = draft.copy(salePrices = safeSalePrices)

          onDraftChanged(
            if (returnPriceManuallyEdited) {
              updated
            } else {
              updated.copy(
                returnPrices = safeSalePrices.map { it.copy(supplierId = "") }
              )
            }
          )
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextField))

      StockPriceGroupEditor(
        title = stateValues.stringReturnPrice,
        placeholder = stateValues.stringEnterReturnPrice,
        prices = draft.returnPrices.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) },
        addText = "${stateValues.stringAdd} ${stateValues.stringReturnPrice}",
        onChanged = {
          onReturnPriceManuallyEditedChanged(true)
          onDraftChanged(
            draft.copy(returnPrices = it.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) })
          )
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextField))

      StockPriceGroupEditor(
        title = stateValues.stringSupplyPrice,
        placeholder = stateValues.stringEnterSupplyPrice,
        prices = draft.supplyPrices.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) },
        addText = "${stateValues.stringAdd} ${stateValues.stringSupplyPrice}",
        onChanged = {
          onDraftChanged(
            draft.copy(supplyPrices = it.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) })
          )
        }
      )

      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
private fun AppConfiguration.StockAddEditOrdersTab(
  modifier: Modifier = Modifier,
  goodsItem: GoodsItemDataModel?
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(stateValues.marginTextField),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    MessageText(
      text = if (goodsItem == null || goodsItem.id.isBlank()) {
        "Save the goods item first, then supplier orders can be attached to it."
      } else {
        "Supplier orders are ready as a tab destination. The order creation and receiving form is the next safe layer to connect."
      }
    )
  }
}

@Composable
fun AppConfiguration.StockAddEditGoodsItemScreen() {
  val addEditState by NavigationScreenModel.Stock.AddEditGoodsItem.state.collectAsState()

  val editedId = addEditState[
    NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID
  ]

  val existing = stateValues.stock.orEmpty().find { it.id == editedId }

  val defaultCurrency = stateValues.globalAppConfiguration
    .countries
    .firstOrNull()
    ?.currencies
    ?.firstOrNull()
    ?.code
    ?: "KZT"

  val defaultMeasurementUnitId = stateValues.globalAppConfiguration
    .goodsItemsQuantityUnits
    .firstOrNull()
    ?.id
    ?: "0"

  fun newDraft(): StockAddEditDraft {
    return StockAddEditDraft(
      barcodes = listOf(""),
      name = emptyLocalizedItemForCurrentLanguage(),
      description = emptyLocalizedItemForCurrentLanguage(),
      measurementUnitId = defaultMeasurementUnitId,
      categoryIds = emptyList(),
      salePrices = listOf(
        PriceDataModel(
          price = "",
          currency = defaultCurrency,
          supplierId = ""
        )
      ),
      returnPrices = listOf(
        PriceDataModel(
          price = "",
          currency = defaultCurrency,
          supplierId = ""
        )
      ),
      supplyPrices = listOf(
        PriceDataModel(
          price = "",
          currency = defaultCurrency,
          supplierId = ""
        )
      ),
      isQuickItem = false,
      note = ""
    )
  }

  var draft by remember(existing?.id, defaultCurrency, defaultMeasurementUnitId, stateValues.appLanguage) {
    mutableStateOf(
      existing?.toStockAddEditDraft() ?: newDraft()
    )
  }

  var selectedTabId by rememberSaveable(existing?.id ?: "new_stock_item") {
    mutableStateOf("info")
  }

  var returnPriceManuallyEdited by rememberSaveable(existing?.id ?: "new_stock_item") {
    mutableStateOf(
      existing?.let {
        it.returnPrices
          .map { price -> price.price to price.currency }
          .toSet() != it.salePrices
          .map { price -> price.price to price.currency }
          .toSet()
      } ?: false
    )
  }

  val canPopStockScreen = !Navigation.Stock.isVeryFirstScreen(stateValues.isNarrowScreen)

  val tabs = listOf(
    StockAddEditTabContent(
      id = "info",
      title = "Info",
      iconPath = stateValues.drawablePathIconEdit
    ),
    StockAddEditTabContent(
      id = "prices",
      title = "Prices",
      iconPath = stateValues.drawablePathIconFinances
    ),
    StockAddEditTabContent(
      id = "batches",
      title = "Batches",
      iconPath = stateValues.drawablePathIconStock
    ),
    StockAddEditTabContent(
      id = "supplier_prices",
      title = "Supplier prices",
      iconPath = stateValues.drawablePathIconSuppliers
    ),
    StockAddEditTabContent(
      id = "orders",
      title = "Orders",
      iconPath = stateValues.drawablePathIconTransactionSupply
    )
  )

  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = if (existing == null) stateValues.stringAddGoodsItem else stateValues.stringEditGoodsItem,
      iconPath = if (existing == null) stateValues.drawablePathIconAdd else stateValues.drawablePathIconEdit,
      onBack = if (canPopStockScreen) {
        {
          coroutineScope.launch {
            Navigation.Stock.pop(stateValues.isNarrowScreen)
            NavigationScreenModel.Stock.AddEditGoodsItem.removeState(
              NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID
            )
          }
        }
      } else null
    )

    StockAddEditTabs(
      modifier = Modifier
        .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.92f),
      selectedId = selectedTabId,
      tabs = tabs,
      onSelected = {
        selectedTabId = it
      }
    )

    when (selectedTabId) {
      "prices" -> {
        StockAddEditPricesTab(
          modifier = Modifier.weight(1f),
          draft = draft,
          defaultCurrency = defaultCurrency,
          returnPriceManuallyEdited = returnPriceManuallyEdited,
          onReturnPriceManuallyEditedChanged = {
            returnPriceManuallyEdited = it
          },
          onDraftChanged = {
            draft = it
          }
        )
      }

      "batches" -> {
        StockAddEditBatchesPage(
          modifier = Modifier.weight(1f),
          goodsItem = existing
        )
      }

      "supplier_prices" -> {
        StockSupplierPricesPage(
          modifier = Modifier.weight(1f),
          goodsItem = existing
        )
      }

      "orders" -> {
        StockAddEditOrdersTab(
          modifier = Modifier.weight(1f),
          goodsItem = existing
        )
      }

      else -> {
        StockAddEditInfoTab(
          modifier = Modifier.weight(1f),
          draft = draft,
          onDraftChanged = {
            draft = it
          }
        )
      }
    }

    if (selectedTabId == "info" || selectedTabId == "prices") {
      actionButton(
        modifier = Modifier
          .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.92f)
          .padding(8.dp),
        text = stateValues.stringConfirm,
        enabled = draft.isValidStockDraft() &&
            stateValues.activeStoreId != null &&
            stateValues.latestNotification == null,
        onClick = {
          val storeId = stateValues.activeStoreId ?: return@actionButton
          val goodsItem = draft.toGoodsItem(storeId, existing)

          if (existing == null) {
            addGoodsItem(goodsItem) {
              if (it is DataState.Success) {
                coroutineScope.launch {
                  Navigation.Stock.pop(stateValues.isNarrowScreen)
                }
              }
            }
          } else {
            updateGoodsItem(goodsItem) {
              if (it is DataState.Success) {
                coroutineScope.launch {
                  Navigation.Stock.pop(stateValues.isNarrowScreen)
                  NavigationScreenModel.Stock.AddEditGoodsItem.removeState(
                    NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID
                  )
                }
              }
            }
          }
        }
      )
    }
  }
}


//@Composable
//fun AppConfiguration.StockAddEditGoodsItemScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val state by NavigationScreenModel.Stock.AddEditGoodsItem.state.collectAsState()
//
//    val editedGoodsItem =
//      state[NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID]?.run { stateValues.stock?.find { goodsItem -> goodsItem.id == this } }
//
//    ScreenAppBarWidget(
//      title = editedGoodsItem?.let { stateValues.stringEditGoodsItem } ?: stateValues.stringAddGoodsItem,
//      iconPath = editedGoodsItem?.let { stateValues.drawablePathIconEdit } ?: stateValues.drawablePathIconAdd,
//      onBack = if (!Navigation.Stock.isVeryFirstScreen(stateValues.isNarrowScreen)) {
//        {
//          coroutineScope.launch {
//            Navigation.Stock.pop(stateValues.isNarrowScreen)
//            if (editedGoodsItem != null)
//              NavigationScreenModel.Stock.AddEditGoodsItem.removeState(NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID)
//          }
//        }
//      } else null
//    )
//
//    var goAction: (() -> Unit)? = null
//
//    var barcodeTextFieldValues by rememberSaveable {
//      mutableStateOf(
//        mutableListOf<String>()
//          .apply {
//            add("")
//          }
//      )
//    }
//
//    LazyColumn(
//      modifier = Modifier
//        .fillMaxWidth()
//        .weight(1f)
//        .padding(start = 8.dp, top = 24.dp, end = 8.dp)
//    ) {
//      item {
//        Text(
//          modifier = Modifier
//            .padding(bottom = 4.dp),
//          text = stateValues.stringBarcode,
//          style = TextStyle(
//            color = stateValues.TextColor,
//            fontSize = stateValues.accentTextSize,
//            fontWeight = FontWeight.Bold
//          )
//        )
//
//        barcodeTextFieldValues.forEachIndexed { index, _ ->
//          Row {
//            genericTextField(
//              placeholderText = if (index == 0) stateValues.stringEnterBarcode else stateValues.stringEnterBarcode + " ${index + 1}",
//              onValueChange = { value, action ->
//                barcodeTextFieldValues[index] = value
//                action()
//              }
//            )
//
//            Spacer(modifier = Modifier.width(1.dp))
//
//            actionButton(
//              text = "",
//              iconPath = stateValues.drawablePathIconSubtract,
//              iconRes = stateValues.drawableResIconSubtract.value
//            ) {
//
//            }
//          }
//
//          Spacer(modifier = Modifier.height(2.dp))
//        }
//
//        Spacer(modifier = Modifier.height(4.dp))
//
//        actionButton(
//          text = stateValues.stringAddBarcode
//        ) {
//          barcodeTextFieldValues = mutableListOf<String>().apply {
//            addAll(barcodeTextFieldValues)
//            add("")
//          }
//
//          println("barcodes $barcodeTextFieldValues")
//
//        }
//
//        var name: String? by rememberSaveable {
//          mutableStateOf(null)
//        }
//
//        var measurementUnitDropdownListSelectedInitial: String? by rememberSaveable {
//          mutableStateOf(stateValues.globalAppConfiguration.goodsItemsQuantityUnits.takeIf { it.isNotEmpty() }
//            ?.first()?.id)
//        }
//
////        LaunchedEffect(barcodeTextFieldGroupContent.data) {
////          try {
////            barcodeTextFieldGroupContent.data.last().value.text.takeIf { it.length == 13 }?.run {
////              genericItemsRepository
////                .getGenericGoodsItems(this)
////                .collect {
////                  if (it is DataState.Success && it.payload.isNotEmpty()) {
////                    name = it.payload.first().name.extractLocalizedString(stateValues.appLanguage)
////                  }
////                }
////            }
////          } catch (thr: Throwable) {
////            thr.printStackTrace()
////          }
////        }
//
//        Spacer(
//          modifier = Modifier
//            .height(stateValues.marginTextField)
//        )
//
//        val nameTextFieldContent =
//          genericTextField(
//            titleText = stateValues.stringName,
//            placeholderText = stateValues.stringEnterName,
//            valueInitial = name,
//            stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
//            stateKey = NavigationScreenModel.KEY_STATE_NAME,
//          )
//
//        var isQuickItem by rememberSaveable {
//          mutableStateOf(false)
//        }
//
//        Row(
//          verticalAlignment = Alignment.CenterVertically
//        ) {
//          Checkbox(
//            checked = isQuickItem,
//            onCheckedChange = {
//              isQuickItem = it
//            },
//            colors = CheckboxColors(
//              checkedBoxColor = stateValues.AccentColor,
//              checkedCheckmarkColor = stateValues.AccentTextColor,
//              uncheckedBoxColor = stateValues.BackgroundColor,
//              checkedBorderColor = stateValues.PlaceholderTextColor,
//              uncheckedBorderColor = stateValues.PlaceholderTextColor,
//              uncheckedCheckmarkColor = stateValues.PlaceholderTextColor,
//              disabledBorderColor = stateValues.PlaceholderTextColor,
//              disabledCheckedBoxColor = stateValues.PlaceholderTextColor,
//              disabledUncheckedBoxColor = stateValues.PlaceholderTextColor,
//              disabledIndeterminateBorderColor = stateValues.PlaceholderTextColor,
//              disabledUncheckedBorderColor = stateValues.PlaceholderTextColor,
//              disabledIndeterminateBoxColor = stateValues.PlaceholderTextColor,
//            )
//          )
//
//          Spacer(Modifier.width(2.dp))
//
//          Text(
//            text = stateValues.stringQuick,
//            color = stateValues.TextColor
//          )
//        }
//
//        var categoryDropdownListContent: DropdownListWidgetContent? = null
//        categoryDropdownListContent = stateValues.goodsCategories?.run {
//          val content = dropdownListWidget(
//            titleText = stateValues.stringCategory,
//            domains = map {
//              SelectableDomain(
//                id = it.id,
//                displayId = it.name,
//                name = it.name,
//                iconPath = null,
//                iconRes = null,
//              )
//            },
//            showName = false
//          )
//
//          Spacer(
//            modifier = Modifier
//              .height(stateValues.marginTextField)
//          )
//
//          content
//        }
//
//        LaunchedEffect(categoryDropdownListContent?.selectedId) {
//          stateValues.goodsCategories?.find { it.id == categoryDropdownListContent?.selectedId }?.let {
//            measurementUnitDropdownListSelectedInitial = it.quantityUnitId
//          }
//        }
//
//        val measurementUnitDropdownListContent = dropdownListWidget(
//          titleText = stateValues.stringMeasurementUnit,
//          domains = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map {
//            SelectableDomain(
//              id = it.id,
//              displayId = it.immutableUnitName,
//              name = it.immutableUnitName,
//              iconPath = null,
//              iconRes = null
//            )
//          },
//          selectedInitial = measurementUnitDropdownListSelectedInitial,
//          showName = false
//        )
//
//        Spacer(
//          modifier = Modifier
//            .height(stateValues.marginTextField)
//        )
//
//        Text(
//          text = "Batch data", // TODO
//          fontSize = stateValues.accentTextSize,
//          fontWeight = FontWeight.Bold,
//          color = stateValues.TextColor,
//          modifier = Modifier
//            .fillMaxWidth()
//        )
//
//        Spacer(
//          modifier = Modifier
//            .height(4.dp)
//        )
//
//        val prices by rememberSaveable {
//          mutableStateOf(
//            editedGoodsItem?.let {
//              mutableListOf<BatchPriceInfo>().apply {
//                it.salePrices.forEach { item ->
//                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
//                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
//                      set(index, get(index).copy(salePrice = item.price))
//                    }
//                  else
//                    add(
//                      BatchPriceInfo(
//                        supplierId = item.supplierId,
//                        supplyPrice = "",
//                        salePrice = item.price,
//                        returnPrice = "",
//                        currency = item.currency
//                      )
//                    )
//                }
//
//                it.supplyPrices.forEach { item ->
//                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
//                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
//                      set(index, get(index).copy(supplyPrice = item.price))
//                    }
//                  else
//                    add(
//                      BatchPriceInfo(
//                        supplierId = item.supplierId,
//                        supplyPrice = item.price,
//                        salePrice = "",
//                        returnPrice = "",
//                        currency = item.currency
//                      )
//                    )
//                }
//
//                it.returnPrices.forEach { item ->
//                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
//                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
//                      set(index, get(index).copy(supplyPrice = item.price))
//                    }
//                  else
//                    add(
//                      BatchPriceInfo(
//                        supplierId = item.supplierId,
//                        supplyPrice = "",
//                        salePrice = "",
//                        returnPrice = item.price,
//                        currency = item.currency
//                      )
//                    )
//                }
//              }
//            } ?: emptyList()
//          )
//        }
//
//        Row(
//          verticalAlignment = Alignment.CenterVertically
//        ) {
//          actionButton(
//            text = "",
//            iconPath = stateValues.drawablePathIconAdd,
//            iconRes = stateValues.drawableResIconAdd.value
//          ) {
//
//          }
//
//          Spacer(modifier = Modifier.width(8.dp))
//
//          LazyRow {
//            items(prices) {
//              StockBatchWidget(
//                modifier = Modifier
//                  .width(stateValues.screenWidth / 3),
//                containedSupplierIds = prices.map { it.supplierId }
//              )
//            }
//          }
//        }
//
//        goAction = {
////          editedGoodsItem?.let {
////
////          } ?: stockRepository
////            .addGoodsItem(
////              GoodsItemDataModel(
////                id = "",
////                userId = "",
////                storeId = stateValues.activeStoreId!!,
////                barcode = barcodeTextFieldGroupContent.data.map { it.value.text },
////                name = listOf(
////                  LocalizedStringDataModel(language = "main", nameTextFieldContent.value.text)
////                ),
////                measurementUnitId = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.find {
////                  it.id == measurementUnitDropdownListContent.selectedId
////                }!!.id,
////                categoryIds = categoryDropdownListContent?.selectedId?.let { listOf(it) } ?: emptyList(),
////                salePrices = saleData.data.map {
////                  PriceDataModel(
////                    price = it.value.text,
////                    currency = it.selectedSecondaryDomainId,
////                    supplierId = it.selectedDomainId
////                  )
////                },
////                supplyPrices = supplyData.data.map {
////                  PriceDataModel(
////                    price = it.value.text,
////                    currency = it.selectedSecondaryDomainId,
////                    supplierId = it.selectedDomainId
////                  )
////                },
////                returnPrices = returnData.data.map {
////                  PriceDataModel(
////                    price = it.value.text,
////                    currency = it.selectedSecondaryDomainId,
////                    supplierId = it.selectedDomainId
////                  )
////                },
////                createdAt = 0L,
////                isQuickItem = isQuickItem,
////                isActive = true
////              )
////            ) {
////              coroutineScope.launch {
////                Navigation.Stock.pop(stateValues.isNarrowScreen)
////                if (editedGoodsItem != null)
////                  NavigationScreenModel.Stock.AddEditGoodsItem.removeState(NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID)
////              }
////            }
//        }
//      }
//
//      item {
//        Spacer(
//          modifier = Modifier
//            .height(stateValues.screenHeight / 4)
//        )
//      }
//    }
//
//    Column(
//      modifier = Modifier
//        .fillMaxWidth()
//        .padding(4.dp)
//    ) {
//      Spacer(modifier = Modifier.height(4.dp))
//
//      actionButton(
//        text = editedGoodsItem?.let { stateValues.stringEditGoodsItem } ?: stateValues.stringAddGoodsItem,
//        enabled = stateValues.latestNotification == null,
//        onClick = {
//          goAction?.invoke()
//        }
//      )
//
//      Spacer(modifier = Modifier.height(4.dp))
//    }
//  }
//}

data class DropdownOption(
  val id: String,
  val title: String,
  val subtitle: String? = null
)

@Composable
fun AppConfiguration.LocalizedStringListEditor(
  title: String,
  values: List<LocalizedStringDataModel>,
  onChanged: (List<LocalizedStringDataModel>) -> Unit
) {
  Column {
    Text(
      text = title,
      color = stateValues.TextColor,
      fontSize = stateValues.textSize,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    stateValues.globalAppConfiguration.languages.forEach { language ->
      val current = values.find { it.language == language.language }?.value.orEmpty()

      SimpleTextInput(
        modifier = Modifier.fillMaxWidth(),
        value = current,
        placeholder = language.name.extractLocalizedString(stateValues.appLanguage)
          ?: language.language,
        onValueChange = { newValue ->
          val mutable = values.toMutableList()
          val index = mutable.indexOfFirst { it.language == language.language }

          if (index == -1) {
            mutable.add(
              LocalizedStringDataModel(
                language = language.language,
                value = newValue
              )
            )
          } else {
            mutable[index] = mutable[index].copy(value = newValue)
          }

          onChanged(mutable)
        }
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextField))
    }
  }
}

@Composable
fun AppConfiguration.SimpleTextInput(
  modifier: Modifier = Modifier,
  value: String,
  placeholder: String,
  singleLine: Boolean = true,
  onValueChange: (String) -> Unit
) {
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = singleLine,
    textStyle = TextStyle(
      color = stateValues.TextColor,
      fontSize = stateValues.textSize
    ),
    modifier = modifier
      .height(if (singleLine) stateValues.textFieldHeight else stateValues.wideTextFieldHeight)
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        stateValues.PlaceholderTextColor,
        RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(stateValues.BackgroundColor)
      .padding(horizontal = stateValues.marginTextFieldGroup),
    decorationBox = { inner ->
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart
      ) {
        if (value.isBlank()) {
          Text(
            text = placeholder,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.textSize
          )
        }

        inner()
      }
    }
  )
}

@Composable
fun AppConfiguration.BarcodeListEditor(
  title: String,
  barcodes: List<String>,
  onChanged: (List<String>) -> Unit
) {
  var focusTargetIndex by rememberSaveable {
    mutableStateOf(0)
  }

  LaunchedEffect(focusTargetIndex, barcodes.size) {
    if (focusTargetIndex >= 0) {
      delay(650)
      focusTargetIndex = -1
    }
  }

  Column {
    val currentBarcodes = barcodes.ifEmpty { listOf("") }

    currentBarcodes.forEachIndexed { index, barcode ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
        verticalAlignment = Alignment.CenterVertically
      ) {
        genericTextField(
          modifier = Modifier.weight(1f),
          titleText = if (index == 0) title else "$title ${index + 1}",
          valueInitial = barcode,
          placeholderText = stateValues.stringEnterBarcode,
          leadingIconPath = stateValues.drawablePathIconBarcodeCamScanner,
          keyboardType = KeyboardType.Text,
          imeWithAction = ImeWithAction(ImeAction.Next),
          isFocusedInitial = index == focusTargetIndex,
          forceRefocus = index == focusTargetIndex,
          showClearButton = false,
          contentInvalidText = stateValues.stringBarcode,
          onContentValidityCheck = { it.isNotBlank() },
          onFilterValue = { value ->
            value.all { char -> char.isDigit() || char.isLetter() }
          },
          onValueChange = { value, applyChange ->
            if (value.all { char -> char.isDigit() || char.isLetter() }) {
              applyChange()
              onChanged(
                currentBarcodes.toMutableList().also {
                  while (it.size <= index) it.add("")
                  it[index] = value
                }
              )
            }
          }
        )

        if (currentBarcodes.size > 1) {
          Box(
            modifier = Modifier
              .height(stateValues.textFieldHeight)
              .wrapContentWidth(),
            contentAlignment = Alignment.Center
          ) {
            actionButton(
              text = "",
              iconPath = stateValues.drawablePathIconCancel,
              iconContentDescription = stateValues.stringDelete,
              fillMaxWidthIfTextPresent = false,
              enabledColor = stateValues.ErrorColor
            ) {
              val newList = currentBarcodes.toMutableList().also {
                if (index in it.indices) it.removeAt(index)
              }
              focusTargetIndex = (index - 1).coerceAtLeast(0)
              onChanged(newList.ifEmpty { listOf("") })
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(stateValues.marginTextField))
    }

    actionButton(
      text = stateValues.stringAddBarcode,
      iconPath = stateValues.drawablePathIconAdd,
      fillMaxWidthIfTextPresent = false
    ) {
      val nextIndex = currentBarcodes.size
      focusTargetIndex = nextIndex
      onChanged(currentBarcodes + "")
    }
  }
}

@Composable
fun AppConfiguration.SimpleDropdownField(
  modifier: Modifier = Modifier,
  title: String,
  selectedId: String?,
  options: List<DropdownOption>,
  placeholder: String,
  onSelected: (String) -> Unit
) {
  var expanded by rememberSaveable {
    mutableStateOf(false)
  }

  val selected = options.find { it.id == selectedId }

  Column(modifier = modifier) {
    Text(
      text = title,
      color = stateValues.TextColor,
      fontSize = stateValues.textSize,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(4.dp))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(stateValues.cornerRadius))
        .border(
          stateValues.unfocusedBorderWidth,
          stateValues.PlaceholderTextColor,
          RoundedCornerShape(stateValues.cornerRadius)
        )
        .background(stateValues.BackgroundColor)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = ripple(color = stateValues.TextColor)
        ) {
          expanded = !expanded
        }
        .padding(stateValues.marginTextFieldGroup)
    ) {
      Column {
        Text(
          text = selected?.title ?: placeholder,
          color = if (selected == null) stateValues.PlaceholderTextColor else stateValues.TextColor,
          fontSize = stateValues.textSize,
          fontWeight = FontWeight.Bold
        )

        selected?.subtitle?.let {
          Text(
            text = it,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
          )
        }
      }
    }

    AnimatedVisibility(expanded) {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = stateValues.screenHeight / 3)
          .clip(RoundedCornerShape(stateValues.cornerRadius))
          .border(
            stateValues.unfocusedBorderWidth,
            stateValues.PlaceholderTextColor,
            RoundedCornerShape(stateValues.cornerRadius)
          )
          .background(stateValues.BackgroundColor)
      ) {
        items(options) { option ->
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.TextColor)
              ) {
                onSelected(option.id)
                expanded = false
              }
              .padding(stateValues.marginTextFieldGroup)
          ) {
            Text(
              text = option.title,
              color = stateValues.TextColor,
              fontWeight = FontWeight.Bold
            )

            option.subtitle?.let {
              Text(
                text = it,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun AppConfiguration.SimpleDialogWidget(
  modifier: Modifier = Modifier,
  title: String,
  positiveAction: Pair<String, () -> Unit>,
  negativeAction: Pair<String, () -> Unit>
) {
  Column(
    modifier = modifier
      .fillMaxWidth(0.4f),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = title,
      fontWeight = FontWeight.Bold,
      fontSize = stateValues.titleTextSize
    )

    Row {
      actionButton(text = positiveAction.first, onClick = positiveAction.second)
      actionButton(text = negativeAction.first, onClick = negativeAction.second)
    }
  }
}

@Composable
fun AppConfiguration.selectableDomainWidget(
  modifier: Modifier = Modifier,
  domain: SelectableDomain,
  state: MutableTransitionState<Boolean>? = null,
  showId: Boolean = true,
  showName: Boolean = false,
  showExpansion: Boolean = false,
  reverseExpandIconPosition: Boolean = false,
  textColor: Color = stateValues.TextColor,
  onClick: (() -> Unit)? = null
): SelectableDomainWidgetContent {

  var expanded by rememberSaveable {
    mutableStateOf(state?.targetState == true)
  }

  LaunchedEffect(expanded) {
    state?.targetState = expanded
  }

  LaunchedEffect(state?.targetState) {
    expanded = state?.targetState == true
  }

  val drawableResIconExpandLess by stateValues.drawableResIconExpandLess.collectAsState()
  val drawableResIconExpandMore by stateValues.drawableResIconExpandMore.collectAsState()

  Row(
    modifier = modifier
      .height(stateValues.textFieldHeight)
      .run {
        onClick?.let {
          clickable(
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = textColor),
            onClick = {
              expanded = !expanded

              it()
            }
          )
        } ?: this
      }
      .padding(),
    verticalAlignment = Alignment.CenterVertically
  ) {

    if (showExpansion && !reverseExpandIconPosition) {
      CpImage(
        modifier = Modifier
          .padding(
            start = stateValues.textFieldIconPadding,
            top = stateValues.textFieldIconPadding,
            bottom = stateValues.textFieldIconPadding
          )
          .fillMaxHeight()
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        url = if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
        fallbackRes = if (expanded) drawableResIconExpandLess else drawableResIconExpandMore,
        contentDescription = if (showName)
          domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(
            stateValues.appLanguage
          )
        else
          domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(
            stateValues.appLanguage
          )
      )
    }

    domain.iconPath?.let {
      CpImage(
        modifier = Modifier
          .padding(stateValues.textFieldIconPadding)
          .fillMaxHeight()
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        url = it,
        fallbackRes = domain.iconRes,
        contentDescription = if (showName)
          domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(
            stateValues.appLanguage
          )
        else
          domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(
            stateValues.appLanguage
          )
      )
    }

    if (showId)
      Text(
        text = domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.id,
        color = textColor,
        modifier = Modifier
          .padding(horizontal = if (!showExpansion) 16.dp else 8.dp, vertical = 8.dp),
        overflow = TextOverflow.Ellipsis
      )


    if (showName && domain.name != null) {
      Spacer(modifier = Modifier.width(8.dp))

      Text(
        text = domain.name.extractLocalizedString(stateValues.appLanguage) ?: "",
        color = textColor,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (reverseExpandIconPosition) {
      CpImage(
        modifier = Modifier
          .padding(
            start = stateValues.textFieldIconPadding,
            top = stateValues.textFieldIconPadding,
            bottom = stateValues.textFieldIconPadding
          )
          .fillMaxHeight()
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        url = if (!showExpansion) {
          ""
        } else {
          if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore
        },
        fallbackRes = if (!showExpansion) {
          null
        } else {
          if (expanded) drawableResIconExpandLess else drawableResIconExpandMore
        },
        contentDescription = if (showName)
          domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(
            stateValues.appLanguage
          )
        else
          domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(
            stateValues.appLanguage
          )
      )
    }
  }

  return SelectableDomainWidgetContent(expanded)
}

data class SelectableDomainWidgetContent(
  var expanded: Boolean
)

class SelectableDomain(
  val id: String,
  val displayId: List<LocalizedStringDataModel>,
  val name: List<LocalizedStringDataModel>?,
  val iconPath: String?,
  val iconRes: DrawableResource?
) : Searchable {

  constructor(
    id: String,
    displayId: String,
    name: String,
    iconPath: String?,
    iconRes: DrawableResource?
  ) : this(id, displayId.toLocalizedSingleMain(), name.toLocalizedSingleMain(), iconPath, iconRes)

  override val exactSearchOperands: List<String> = mutableListOf<String>().apply {
    addAll(displayId.map { it.value })
    name?.let { addAll(name.map { it.value }) }
  }

  override val containsSearchOperands: List<String> = mutableListOf<String>().apply {
    addAll(displayId.map { it.value })
    name?.let { addAll(name.map { it.value }) }
  }
  override val uniqueSearchOperands: List<String> = mutableListOf<String>().apply {
    addAll(displayId.map { it.value })
    name?.let { addAll(name.map { it.value }) }
  }
}

@Composable
fun AppConfiguration.ScreenAppBarWidget(
  modifier: Modifier = Modifier,
  title: String,
  iconPath: String? = null,
  iconRes: DrawableResource? = null,
  textColor: Color = stateValues.TextColor,
  cornerRadius: Dp = stateValues.cornerRadius,
  leadingContent: @Composable (() -> Unit)? = null,
  trailingIcons: List<Triple<String, DrawableResource, () -> Unit>> = emptyList(),
  onBack: (() -> Unit)? = null
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
  ) {
    Row(
      modifier = modifier
        .run {
          if (stateValues.isNarrowScreen)
            clip(
              RoundedCornerShape(
                bottomStart = cornerRadius,
                bottomEnd = cornerRadius
              )
            )
          else
            this
        }
        .run {
          if (stateValues.isNarrowScreen)
            border(
              stateValues.unfocusedBorderWidth,
              stateValues.PlaceholderTextColor,
              RoundedCornerShape(
                bottomStart = cornerRadius,
                bottomEnd = cornerRadius
              )
            )
          else
            this
        }
        .fillMaxWidth()
        .height(48.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      onBack?.run {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .clickable(
              interactionSource = remember {
                MutableInteractionSource()
              },
              indication = ripple(color = textColor, radius = cornerRadius),
              onClick = this
            )
        ) {
          val iconRes by stateValues.drawableResIconBackArrow.collectAsState()

          CpImage(
            modifier = Modifier
              .padding(16.dp)
              .aspectRatio(1f, matchHeightConstraintsFirst = true),
            url = stateValues.drawablePathIconBackArrow,
            fallbackRes = iconRes,
            contentDescription = stateValues.stringBack
          )
        }
      }

      leadingContent?.invoke()

      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (iconPath != null) {
          CpImage(
            modifier = Modifier
              .padding(start = 0.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)
              .aspectRatio(1f, matchHeightConstraintsFirst = true),
            url = iconPath,
            fallbackRes = iconRes,
            contentDescription = title
          )
        }

        Text(
          text = title,
          modifier = modifier,
          textAlign = TextAlign.Center,
          fontWeight = FontWeight.Bold,
          fontSize = stateValues.accentTextSize,
          color = textColor
        )
      }

      trailingIcons.takeIf { it.isNotEmpty() }?.run {
        forEach {
          Spacer(modifier = Modifier.width(8.dp))

          actionButton(
            text = "",
            iconPath = it.first,
            iconRes = it.second,
            onClick = it.third
          )
        }

        Spacer(modifier = Modifier.width(16.dp))
      }
    }

    if (!stateValues.isNarrowScreen)
      Spacer(
        modifier = Modifier
          .background(stateValues.PlaceholderTextColor)
          .fillMaxWidth()
          .height(stateValues.unfocusedBorderWidth)
      )
  }
}

@Composable
fun AppConfiguration.searchTextField(
  modifier: Modifier = Modifier,
  valueInitial: String? = null,
  stateHost: StateHost?,
  stateKey: String?,
  isFocusedInitial: Boolean = false,
  updateIsFocusedAction: ((FocusState) -> Unit)? = null,
  forceRefocus: Boolean = false,
  barcodeCamScanner: Boolean = false,
  focusedBorderWidth: Dp = stateValues.focusedBorderWidth,
  unfocusedBorderWidth: Dp = stateValues.unfocusedBorderWidth,
  focusedBorderColor: Color = stateValues.AccentColor,
  unfocusedBorderColor: Color = stateValues.PlaceholderTextColor
): GenericTextFieldContent {
  var textFieldContent: GenericTextFieldContent? = null

  Column(
    modifier = modifier
  ) {
    val barcodeCamScanningExpansionState = remember {
      MutableTransitionState(false)
        .apply {
          targetState = false
        }
    }

    textFieldContent = genericTextField(
      valueInitial = valueInitial,
      stateHost = stateHost,
      stateKey = stateKey,
      isFocusedInitial = isFocusedInitial,
      updateIsFocusedAction = updateIsFocusedAction,
      forceRefocus = forceRefocus,
      placeholderText = stateValues.stringSearchByAnyData,
      focusedBorderWidth = focusedBorderWidth,
      unfocusedBorderWidth = unfocusedBorderWidth,
      focusedBorderColor = focusedBorderColor,
      unfocusedBorderColor = unfocusedBorderColor,
      leadingIconPath = stateValues.drawablePathIconSearch,
      trailingIconExtraPath = if (barcodeCamScanner) stateValues.drawablePathIconBarcodeCamScanner else null,
      trailingIconExtraOnClick = if (barcodeCamScanner) {
        {
          barcodeCamScanningExpansionState.targetState =
            !barcodeCamScanningExpansionState.targetState
        }
      } else null,
    )

    AnimatedVisibility(
      modifier = Modifier
        .clip(RoundedCornerShape(stateValues.cornerRadius))
        .wrapContentHeight()
        .height(100.dp),
      visibleState = barcodeCamScanningExpansionState,
      enter = expandVertically(animationSpec = tween(100)),
      exit = shrinkVertically(animationSpec = tween(100))
    ) {

    }
  }

  return textFieldContent!!
}

@Composable
fun AppConfiguration.repeatedPasswordTextFieldGroup(
  modifier: Modifier = Modifier,
  stateHost: StateHost,
  stateKey: String,
  repeatedStateKey: String,
  passwordTitleText: String? = null,
  passwordPlaceholderText: String? = null,
  repeatPasswordTitleText: String? = null,
  repeatPasswordPlaceholderText: String? = null,
  imeWithAction: ImeWithAction? = null
): Pair<GenericTextFieldContent, GenericTextFieldContent> {

  var showPassword by rememberSaveable {
    mutableStateOf(false)
  }

  val passwordTextFieldContent = genericTextField(
    modifier = modifier,
    stateHost = stateHost,
    stateKey = stateKey,
    titleText = passwordTitleText ?: stateValues.stringPassword,
    placeholderText = passwordPlaceholderText ?: stateValues.stringEnterPassword,
    leadingIconPath = stateValues.drawablePathIconPassword,
    keyboardType = KeyboardType.Password,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    trailingIconExtraPath = if (showPassword) stateValues.drawablePathIconEyeHide else stateValues.drawablePathIconEyeShow,
    trailingIconExtraOnClick = {
      showPassword = !showPassword
    },
    contentInvalidText = stateValues.stringPasswordMustBe,
    onContentValidityCheck = {
      it.checkAsPassword()
    },
    visualTransformation =
      if (showPassword) {
        {
          getTransformedTextWithSelectionFocusTextColor(
            it,
            stateValues.AccentTextColor
          )
        }
      } else {
        {
          getPasswordTransformedTextWithSelectionFocusTextColor(
            it,
            stateValues.AccentTextColor
          )
        }
      }
  )

  Spacer(modifier = Modifier
    .height(stateValues.marginTextField)
  )

  val repeatedPasswordTextFieldContent = genericTextField(
    modifier = modifier,
    stateHost = stateHost,
    stateKey = repeatedStateKey,
    titleText = repeatPasswordTitleText ?: stateValues.stringRepeatPassword,
    placeholderText = repeatPasswordPlaceholderText ?: stateValues.stringRepeatPassword,
    leadingIconPath = stateValues.drawablePathIconPassword,
    keyboardType = KeyboardType.Password,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    contentInvalidText = stateValues.stringPasswordsMustMatch,
    onContentValidityCheck = {
      it == passwordTextFieldContent.value.text
    },
    visualTransformation =
      if (showPassword) {
        {
          getTransformedTextWithSelectionFocusTextColor(
            it,
            stateValues.AccentTextColor
          )
        }
      } else {
        {
          getPasswordTransformedTextWithSelectionFocusTextColor(
            it,
            stateValues.AccentTextColor
          )
        }
      }
  )

  return Pair(passwordTextFieldContent, repeatedPasswordTextFieldContent)
}

@Composable
fun AppConfiguration.passwordTextField(
  modifier: Modifier = Modifier,
  titleText: String? = null,
  placeholderText: String? = null,
  stateHost: StateHost,
  stateKey: String,
  contentInvalidText: String? = null,
  imeWithAction: ImeWithAction? = null
): GenericTextFieldContent {

  var showPassword by rememberSaveable {
    mutableStateOf(false)
  }

  return genericTextField(
    modifier = modifier,
    stateHost = stateHost,
    stateKey = stateKey,
    titleText = titleText ?: stateValues.stringPassword,
    placeholderText = placeholderText ?: stateValues.stringEnterPassword,
    leadingIconPath = stateValues.drawablePathIconPassword,
    keyboardType = KeyboardType.Password,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    trailingIconExtraPath = if (showPassword) stateValues.drawablePathIconEyeHide else stateValues.drawablePathIconEyeShow,
    trailingIconExtraOnClick = {
      showPassword = !showPassword
    },
    contentInvalidText = contentInvalidText ?: stateValues.stringPasswordMustBe,
    onContentValidityCheck = {
      it.checkAsPassword()
    },
    visualTransformation =
      if (showPassword) {
        {
          getTransformedTextWithSelectionFocusTextColor(
            it,
            stateValues.AccentTextColor
          )
        }
      } else {
        {
          getPasswordTransformedTextWithSelectionFocusTextColor(
            it,
            stateValues.AccentTextColor
          )
        }
      }
  )
}

sealed class NavigationScreenModel(
  val route: String,
  open val name: String = route,
  open val iconPath: String = "",
  open val iconRes: DrawableResource? = null
): StateHost() {

  companion object {
    const val KEY_STATE_SEARCH_QUERY: String = "keyState_searchQuery"
    const val KEY_STATE_NAME: String = "keyState_name"
    const val KEY_STATE_PHONE_NUMBER: String = "keyState_phoneNumber"
    const val KEY_STATE_EMAIL: String = "keyState_email"
    const val KEY_STATE_FIRST_NAME: String = "keyState_firstName"
    const val KEY_STATE_LAST_NAME: String = "keyState_lastName"
    const val KEY_STATE_PASSWORD: String = "keyState_password"
    const val KEY_STATE_REPEATED_PASSWORD: String = "keyState_repeatedPassword"
    const val KEY_STATE_QUANTITY: String = "keyState_quantity"
  }

  sealed class Buyer(route: String): NavigationScreenModel(route) {

    sealed class Main(route: String): Buyer(route) {
      data object Home: Main("BuyerMainHomeNavigationScreenModelRoute") {
        override val name: String
          get() = AppConfiguration.stateValues.stringMain // TODO
        override val iconPath: String
          get() {
            return AppConfiguration.stateValues.drawablePathIconMenu // TODO
          }
        override val iconRes: DrawableResource
          get() {
            return AppConfiguration.stateValues.drawableResIconMenu.value
          }
      }
      data object Search: Main("BuyerMainSearchNavigationScreenModelRoute")
    }

    sealed class Cart(route: String): Buyer(route) {
      data object Main: Cart("BuyerCartMainNavigationScreenModelRoute") {
        override val name: String
          get() = AppConfiguration.stateValues.stringCart
        override val iconPath: String
          get() {
            return AppConfiguration.stateValues.drawablePathIconTransactionSale
          }
        override val iconRes: DrawableResource
          get() {
            return AppConfiguration.stateValues.drawableResIconTransactionSale.value
          }
      }
    }

    sealed class Orders(route: String): Buyer(route) {
      data object Main: Orders("BuyerOrdersMainNavigationScreenModelRoute") {
        override val name: String
          get() = "Orders" // TODO
        override val iconPath: String
          get() {
            return AppConfiguration.stateValues.drawablePathIconTransactionHistory
          }
        override val iconRes: DrawableResource
          get() {
            return AppConfiguration.stateValues.drawableResIconTransactionHistory.value
          }
      }
    }
  }

  sealed class Transaction(route: String): NavigationScreenModel(route) {

    data object MainSale: Transaction("TransactionMainSaleNavigationScreenModelRoute") {
      override val iconPath: String
        get() {
          return AppConfiguration.stateValues.drawablePathIconTransactionSale
        }
      override val iconRes: DrawableResource
        get() {
          return AppConfiguration.stateValues.drawableResIconTransactionSale.value
        }
      override val name: String
        get() = AppConfiguration.stateValues.stringSale
    }

    data object MainReturn: Transaction("TransactionMainReturnNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionReturn
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconTransactionReturn.value
      override val name: String
        get() = AppConfiguration.stateValues.stringReturn
    }

    data object MainSupply: Transaction("TransactionMainSupplyNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionSupply
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconTransactionSupply.value
      override val name: String
        get() = AppConfiguration.stateValues.stringSupply
    }

    data object Cart: Transaction("TransactionCartScreenNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconCart
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconCart.value
      override val name: String
        get() = AppConfiguration.stateValues.stringCart
    }

    data object Selection: Transaction("TransactionSelectionNavigationScreenModelRoute") {
      override val iconPath: String
        get() = ""
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconCart.value
      override val name: String
        get() = AppConfiguration.stateValues.stringSelect
    }

    data object Payment: Transaction("TransactionPaymentNavigationScreenModelRoute") {
      override val iconPath: String
        get() = ""
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconCart.value
      override val name: String
        get() = AppConfiguration.stateValues.stringPayment
    }

    data object ReceiptPreview: Transaction("TransactionReceiptPreviewNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconReceipt
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconReceipt.value
      override val name: String
        get() = AppConfiguration.stateValues.stringReceipt
    }
  }

  sealed class Stock(route: String): NavigationScreenModel(route) {

    data object Main: Stock("StockNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconStock
      override val name: String
        get() = AppConfiguration.stateValues.stringStock
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconStock.value
    }

    data object Warehouse: Stock("StockWarehouseNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object AddEditGoodsItem: Stock("StockAddGoodsItemNavigationScreenModelRoute") {
      const val KEY_STATE_EDITED_GOODS_ITEM_ID: String = "keyState_editedGoodsItemId"
      const val KEY_STATE_BARCODE: String = "keyState_barcode"
      const val KEY_STATE_SALE_DATA: String = "keyState_saleData"
      const val KEY_STATE_RETURN_DATA: String = "keyState_returnData"
      const val KEY_STATE_SUPPLY_DATA: String = "keyState_supplyData"

      const val KEY_STATE_QUANTITY_DATA: String = "keyState_quantityData"
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object GoodsItemDetails: Stock("StockGoodsItemDetailsNavigationScreenModelRoute") {
      const val KEY_STATE_GOODS_ITEM_ID: String = "keyState_goodsItemId"
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object GoodsItemBatches: Stock("StockGoodsItemBatchesNavigationScreenModelRoute") {
      const val KEY_STATE_GOODS_ITEM_ID: String = "keyState_goodsItemId"
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object GoodsItemSupplierPrices: Stock("StockGoodsItemSupplierPricesNavigationScreenModelRoute") {
      const val KEY_STATE_GOODS_ITEM_ID: String = "keyState_goodsItemId"
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object GoodsItemOrders: Stock("StockGoodsItemOrdersNavigationScreenModelRoute") {
      const val KEY_STATE_GOODS_ITEM_ID: String = "keyState_goodsItemId"
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }
  }

  sealed class Menu(route: String): NavigationScreenModel(route) {

    data object Main: Menu("MenuNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconMenu
      override val name: String
        get() = AppConfiguration.stateValues.stringMenu
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconMenu.value
    }

    data object List: Menu("MenuListNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object UserAccount: Menu("MenuUserAccountNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconUserAccount
      override val name: String
        get() = AppConfiguration.stateValues.stringUserAccount
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconUserAccount.value
      const val KEY_STATE_CONFIRMATION_PASSWORD: String = "keyState_confirmationPassword"
    }

    data object Finances: Menu("MenuFinancesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconFinances
      override val name: String
        get() = AppConfiguration.stateValues.stringFinances
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconFinances.value
    }

    data object AppMode: Menu("MenuAppModeNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconSwitch
      override val name: String
        get() = AppConfiguration.stateValues.stringAppMode
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconSwitch.value
    }

    data object StoreSubscription: Menu("MenuStoreSubscriptionNavigationScreenModelRoute") {
      override val iconPath: String
        get() = ""
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconStores.value
      override val name: String
        get() = AppConfiguration.stateValues.stringSubscription
    }

    data object StoreSubscriptionPlans: Menu("MenuStoreSubscriptionPlansNavigationScreenModelRoute") {
      override val iconPath: String
        get() = ""
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconStores.value
      override val name: String
        get() = AppConfiguration.stateValues.stringSubscriptionPlans
    }

    data object Workers: Menu("MenuWorkersNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconWorkers
      override val name: String
        get() = AppConfiguration.stateValues.stringWorkers
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconWorkers.value
    }
    data object AddEditWorker: Menu("MenuAddEditWorkerNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconWorkers
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconWorkers.value
      override val name: String
        get() = AppConfiguration.stateValues.stringAddWorker
    }

    data object Stores: Menu("MenuStoresNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconStores
      override val name: String
        get() = AppConfiguration.stateValues.stringStores
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconStores.value
    }
    data object AddEditStore: Menu("MenuAddEditStoreNavigationScreenModelRoute") {
      const val KEY_STATE_EDITED_STORE_ID: String = "keyState_editedStoreId"
      const val KEY_STATE_ALIAS: String = "keyState_alias"
      const val KEY_STATE_DESCRIPTION: String = "keyState_description"

      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object Analytics: Menu("MenuAnalyticsNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAnalytics
      override val name: String
        get() = AppConfiguration.stateValues.stringAnalytics
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconAnalytics.value
    }

    data object TransactionHistory: Menu("MenuTransactionHistoryNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconTransactionHistory
      override val name: String
        get() = AppConfiguration.stateValues.stringTransactionHistory
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconTransactionHistory.value
    }
    data object TransactionHistoryReceiptPreview: Menu("MenuTransactionHistoryReceiptPreviewNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object Debtors: Menu("MenuDebtorsNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconDebtors
      override val name: String
        get() = AppConfiguration.stateValues.stringDebtors
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconDebtors.value
    }
    data object CloseDebt: Menu("MenuCloseDebtNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object Suppliers: Menu("MenuSuppliersNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconSuppliers
      override val name: String
        get() = AppConfiguration.stateValues.stringSuppliers
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconSuppliers.value
    }
    data object AddEditSupplier: Menu("MenuAddEditSupplierNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object GoodsCategories: Menu("MenuGoodsCategoriesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconGoodsCategories
      override val name: String
        get() = AppConfiguration.stateValues.stringGoodsCategories
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconGoodsCategories.value
    }
    data object AddEditGoodsCategory: Menu("MenuAddEditGoodsCategoryNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconGoodsCategories
      override val name: String
        get() = AppConfiguration.stateValues.stringGoodsCategories
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconGoodsCategories.value
    }

    data object Devices: Menu("MenuDevicesNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconDevices
      override val name: String
        get() = AppConfiguration.stateValues.stringDevices
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconDevices.value
    }
    data object AppLanguage: Menu("MenuAppLanguageNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAppLanguage
      override val name: String
        get() = AppConfiguration.stateValues.stringAppLanguage
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconAppLanguage.value
    }
    data object AppTheme: Menu("MenuAppThemeNavigationScreenModelRoute") {
      override val iconPath: String
        get() = AppConfiguration.stateValues.drawablePathIconAppTheme
      override val name: String
        get() = AppConfiguration.stateValues.stringAppTheme
      override val iconRes: DrawableResource
        get() = AppConfiguration.stateValues.drawableResIconAppTheme.value
    }
  }

  sealed class UserAuth(route: String): NavigationScreenModel(route) {

    data object Main: UserAuth("UserAuthNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object LogIn: UserAuth("UserAuthLogInNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }

    data object SignUp: UserAuth("UserAuthLogInNavigationScreenModelRoute") {
      override val iconRes: DrawableResource
        get() = TODO("Not yet implemented")
    }
  }

  object Splash: NavigationScreenModel("SplashNavigationScreenModelRoute") {
    override val iconRes: DrawableResource
      get() = TODO("Not yet implemented")
  }
}

object Navigation {
  val bottomNavBarScreensStore = listOf(
    NavigationScreenModel.Transaction.MainSale,
    NavigationScreenModel.Transaction.MainReturn,
    NavigationScreenModel.Transaction.MainSupply,
    NavigationScreenModel.Stock.Main,
    NavigationScreenModel.Menu.Main
  )

  val bottomNavBarScreensBuyer = listOf(
    NavigationScreenModel.Buyer.Main.Home,
    NavigationScreenModel.Buyer.Cart.Main,
    NavigationScreenModel.Buyer.Orders.Main,
    NavigationScreenModel.Menu.Main
  )

  private val _Main =
    MutableStateFlow<List<NavigationScreenModel>>(
      listOf(
        NavigationScreenModel.Splash
      )
    )
  val Main = _Main.asStateFlow()

  suspend fun goMain(model: NavigationScreenModel) {
    if (model::class != _Main.value.last()::class)
      _Main.emit(listOf(model))
  }

  fun getCurrentTransactionScreens(transactionTypeIndex: Int, clientId: Int, isNarrowScreen: Boolean): StateFlow<List<NavigationScreenModel.Transaction>> {
    return when (transactionTypeIndex) {
      0 -> {
        when (clientId) {
          0 -> if (isNarrowScreen) TransactionSale.LeftClient1 else TransactionSale.RightClient1
          1 -> if (isNarrowScreen) TransactionSale.LeftClient2 else TransactionSale.RightClient2
          2 -> if (isNarrowScreen) TransactionSale.LeftClient3 else TransactionSale.RightClient3
          3 -> if (isNarrowScreen) TransactionSale.LeftClient4 else TransactionSale.RightClient4
          else -> if (isNarrowScreen) TransactionSale.LeftClient5 else TransactionSale.RightClient5
        }
      }
      1 -> {
        when (clientId) {
          0 -> if (isNarrowScreen) TransactionReturn.LeftClient1 else TransactionReturn.RightClient1
          1 -> if (isNarrowScreen) TransactionReturn.LeftClient2 else TransactionReturn.RightClient2
          2 -> if (isNarrowScreen) TransactionReturn.LeftClient3 else TransactionReturn.RightClient3
          3 -> if (isNarrowScreen) TransactionReturn.LeftClient4 else TransactionReturn.RightClient4
          else -> if (isNarrowScreen) TransactionReturn.LeftClient5 else TransactionReturn.RightClient5
        }
      }
      else -> {
        when (clientId) {
          0 -> if (isNarrowScreen) TransactionSupply.LeftClient1 else TransactionSupply.RightClient1
          1 -> if (isNarrowScreen) TransactionSupply.LeftClient2 else TransactionSupply.RightClient2
          2 -> if (isNarrowScreen) TransactionSupply.LeftClient3 else TransactionSupply.RightClient3
          3 -> if (isNarrowScreen) TransactionSupply.LeftClient4 else TransactionSupply.RightClient4
          else -> if (isNarrowScreen) TransactionSupply.LeftClient5 else TransactionSupply.RightClient5
        }
      }
    }
  }

  object TransactionSale {

    suspend fun go(
      model: NavigationScreenModel.Transaction,
      isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
      remove: Boolean = false
    ) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> goLeftClient1(model, remove)
          1 -> goLeftClient2(model, remove)
          2 -> goLeftClient3(model, remove)
          3 -> goLeftClient4(model, remove)
          else -> goLeftClient5(model, remove)
        }
      } else {
        when (ClientId.value) {
          0 -> goRightClient1(model, remove)
          1 -> goRightClient2(model, remove)
          2 -> goRightClient3(model, remove)
          3 -> goRightClient4(model, remove)
          else -> goRightClient5(model, remove)
        }
      }
    }

    suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> popLeftClient1(navigateAfterwards)
          1 -> popLeftClient2(navigateAfterwards)
          2 -> popLeftClient3(navigateAfterwards)
          3 -> popLeftClient4(navigateAfterwards)
          else -> popLeftClient5(navigateAfterwards)
        }
      } else {
        when (ClientId.value) {
          0 -> popRightClient1(navigateAfterwards)
          1 -> popRightClient2(navigateAfterwards)
          2 -> popRightClient3(navigateAfterwards)
          3 -> popRightClient4(navigateAfterwards)
          else -> popRightClient5(navigateAfterwards)
        }
      }
    }

    suspend fun clear(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> clearLeftClient1()
          1 -> clearLeftClient2()
          2 -> clearLeftClient3()
          3 -> clearLeftClient4()
          else -> clearLeftClient5()
        }
      } else {
        when (ClientId.value) {
          0 -> clearRightClient1()
          1 -> clearRightClient2()
          2 -> clearRightClient3()
          3 -> clearRightClient4()
          else -> clearRightClient5()
        }
      }
    }

    fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
      return (
          if (isNarrowScreen)
            when (clientId) {
              0 -> _LeftClient1
              1 -> _LeftClient2
              2 -> _LeftClient3
              3 -> _LeftClient4
              else -> _LeftClient5
            }
          else
            when (clientId) {
              0 -> _RightClient1
              1 -> _RightClient2
              2 -> _RightClient3
              3 -> _RightClient4
              else -> _RightClient5
            }
          ).value.size == 1
    }

    fun isVeryFirstScreenLeft(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _LeftClient1
        1 -> _LeftClient2
        2 -> _LeftClient3
        3 -> _LeftClient4
        else -> _LeftClient5
      }).value.size == 1
    }

    fun isVeryFirstScreenRight(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _RightClient1
        1 -> _RightClient2
        2 -> _RightClient3
        3 -> _RightClient4
        else -> _RightClient5
      }).value.size == 1
    }

    private val _ClientId =
      MutableStateFlow(0)
    val ClientId =
      _ClientId.asStateFlow()

    suspend fun setClientId(id: Int) {
      _ClientId.emit(id)
    }

    private val _LeftClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient1 =
      _LeftClient1.asStateFlow()

    private val _LeftClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient2 =
      _LeftClient2.asStateFlow()

    private val _LeftClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient3 =
      _LeftClient3.asStateFlow()

    private val _LeftClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient4 =
      _LeftClient4.asStateFlow()

    private val _LeftClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient5 =
      _LeftClient5.asStateFlow()

    private val _RightClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient1 =
      _RightClient1.asStateFlow()

    private val _RightClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient2 =
      _RightClient2.asStateFlow()

    private val _RightClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient3 =
      _RightClient3.asStateFlow()

    private val _RightClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient4 =
      _RightClient4.asStateFlow()

    private val _RightClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient5 =
      _RightClient5.asStateFlow()

    suspend fun goLeftClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient1.value.last()::class)
        _LeftClient1.emit(
          _LeftClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
        popRightClient1()

      val oldSize = _LeftClient1.value.size

      _LeftClient1.emit(
        _LeftClient1.value.toMutableList()
          .apply {
            if (_LeftClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient2.value.last()::class)
        _LeftClient2.emit(
          _LeftClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
        popRightClient2()

      val oldSize = _LeftClient2.value.size

      _LeftClient2.emit(
        _LeftClient2.value.toMutableList()
          .apply {
            if (_LeftClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient2(this@run)
      }
    }

    suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient2.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient3.value.last()::class)
        _LeftClient3.emit(
          _LeftClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
        popRightClient3()

      val oldSize = _LeftClient3.value.size

      _LeftClient3.emit(
        _LeftClient3.value.toMutableList()
          .apply {
            if (_LeftClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient3(this@run)
      }
    }

    suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient3.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient4.value.last()::class)
        _LeftClient4.emit(
          _LeftClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
        popRightClient4()

      val oldSize = _LeftClient4.value.size

      _LeftClient4.emit(
        _LeftClient4.value.toMutableList()
          .apply {
            if (_LeftClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient4.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient5.value.last()::class)
        _LeftClient5.emit(
          _LeftClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
        popRightClient5()

      val oldSize = _LeftClient5.value.size

      _LeftClient5.emit(
        _LeftClient5.value.toMutableList()
          .apply {
            if (_LeftClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient5.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient1.value.last()::class)
        _RightClient1.emit(
          _RightClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient1.value.size

      _RightClient1.emit(
        _RightClient1.value.toMutableList()
          .apply {
            if (_RightClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient1(this@run)
      }
    }

    suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient2.value.last()::class)
        _RightClient2.emit(
          _RightClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient2.value.size

      _RightClient2.emit(
        _RightClient2.value.toMutableList()
          .apply {
            if (_RightClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient2(this@run)
      }
    }

    suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient2.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient3.value.last()::class)
        _RightClient3.emit(
          _RightClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient3.value.size

      _RightClient3.emit(
        _RightClient3.value.toMutableList()
          .apply {
            if (_RightClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient3(this@run)
      }
    }

    suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient3.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient4.value.last()::class)
        _RightClient4.emit(
          _RightClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient4.value.size

      _RightClient4.emit(
        _RightClient4.value.toMutableList()
          .apply {
            if (_RightClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient4(this@run)
      }
    }

    suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient4.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient5.value.last()::class)
        _RightClient5.emit(
          _RightClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient5.value.size

      _RightClient5.emit(
        _RightClient5.value.toMutableList()
          .apply {
            if (_RightClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient5(this@run)
      }
    }

    suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient5.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      TransactionSale.run {
        if (isNarrowScreen) {
          if (RightClient1.value.size > 1) {
            _LeftClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient1.value.subList(1, RightClient1.value.size)
                )
              }
            )
          }
          clearRightClient1()

          if (RightClient2.value.size > 1) {
            _LeftClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient2.value.subList(1, RightClient2.value.size)
                )
              }
            )
          }
          clearRightClient2()

          if (RightClient3.value.size > 1) {
            _LeftClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient3.value.subList(1, RightClient3.value.size)
                )
              }
            )
          }
          clearRightClient3()

          if (RightClient4.value.size > 1) {
            _LeftClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient4.value.subList(1, RightClient4.value.size)
                )
              }
            )
          }
          clearRightClient4()

          if (RightClient5.value.size > 1) {
            _LeftClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient5.value.subList(1, RightClient5.value.size)
                )
              }
            )
          }
          clearRightClient5()

        } else {
          if (LeftClient1.value.size > 1) {
            _RightClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient1.value.subList(1, LeftClient1.value.size)
                )
              }
            )
          }
          clearLeftClient1()

          if (LeftClient2.value.size > 1) {
            _RightClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient2.value.subList(1, LeftClient2.value.size)
                )
              }
            )
          }
          clearLeftClient2()

          if (LeftClient3.value.size > 1) {
            _RightClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient3.value.subList(1, LeftClient3.value.size)
                )
              }
            )
          }
          clearLeftClient3()

          if (LeftClient4.value.size > 1) {
            _RightClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient4.value.subList(1, LeftClient4.value.size)
                )
              }
            )
          }
          clearLeftClient4()

          if (LeftClient5.value.size > 1) {
            _RightClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient5.value.subList(1, LeftClient5.value.size)
                )
              }
            )
          }
          clearLeftClient5()
        }
      }
    }
  }

  object TransactionReturn {

    suspend fun go(
      model: NavigationScreenModel.Transaction,
      isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
      remove: Boolean = false
    ) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> goLeftClient1(model, remove)
          1 -> goLeftClient2(model, remove)
          2 -> goLeftClient3(model, remove)
          3 -> goLeftClient4(model, remove)
          else -> goLeftClient5(model, remove)
        }
      } else {
        when (ClientId.value) {
          0 -> goRightClient1(model, remove)
          1 -> goRightClient2(model, remove)
          2 -> goRightClient3(model, remove)
          3 -> goRightClient4(model, remove)
          else -> goRightClient5(model, remove)
        }
      }
    }

    suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> popLeftClient1(navigateAfterwards)
          1 -> popLeftClient2(navigateAfterwards)
          2 -> popLeftClient3(navigateAfterwards)
          3 -> popLeftClient4(navigateAfterwards)
          else -> popLeftClient5(navigateAfterwards)
        }
      } else {
        when (ClientId.value) {
          0 -> popRightClient1(navigateAfterwards)
          1 -> popRightClient2(navigateAfterwards)
          2 -> popRightClient3(navigateAfterwards)
          3 -> popRightClient4(navigateAfterwards)
          else -> popRightClient5(navigateAfterwards)
        }
      }
    }

    suspend fun clear(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> clearLeftClient1()
          1 -> clearLeftClient2()
          2 -> clearLeftClient3()
          3 -> clearLeftClient4()
          else -> clearLeftClient5()
        }
      } else {
        when (ClientId.value) {
          0 -> clearRightClient1()
          1 -> clearRightClient2()
          2 -> clearRightClient3()
          3 -> clearRightClient4()
          else -> clearRightClient5()
        }
      }
    }

    fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
      return (
          if (isNarrowScreen)
            when (clientId) {
              0 -> _LeftClient1
              1 -> _LeftClient2
              2 -> _LeftClient3
              3 -> _LeftClient4
              else -> _LeftClient5
            }
          else
            when (clientId) {
              0 -> _RightClient1
              1 -> _RightClient2
              2 -> _RightClient3
              3 -> _RightClient4
              else -> _RightClient5
            }
          ).value.size == 1
    }

    fun isVeryFirstScreenLeft(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _LeftClient1
        1 -> _LeftClient2
        2 -> _LeftClient3
        3 -> _LeftClient4
        else -> _LeftClient5
      }).value.size == 1
    }

    fun isVeryFirstScreenRight(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _RightClient1
        1 -> _RightClient2
        2 -> _RightClient3
        3 -> _RightClient4
        else -> _RightClient5
      }).value.size == 1
    }

    private val _ClientId =
      MutableStateFlow(0)
    val ClientId =
      _ClientId.asStateFlow()

    suspend fun setClientId(id: Int) {
      _ClientId.emit(id)
    }

    private val _LeftClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient1 =
      _LeftClient1.asStateFlow()

    private val _LeftClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient2 =
      _LeftClient2.asStateFlow()

    private val _LeftClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient3 =
      _LeftClient3.asStateFlow()

    private val _LeftClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient4 =
      _LeftClient4.asStateFlow()

    private val _LeftClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient5 =
      _LeftClient5.asStateFlow()

    private val _RightClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient1 =
      _RightClient1.asStateFlow()

    private val _RightClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient2 =
      _RightClient2.asStateFlow()

    private val _RightClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient3 =
      _RightClient3.asStateFlow()

    private val _RightClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient4 =
      _RightClient4.asStateFlow()

    private val _RightClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient5 =
      _RightClient5.asStateFlow()

    suspend fun goLeftClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient1.value.last()::class)
        _LeftClient1.emit(
          _LeftClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
        popRightClient1()

      val oldSize = _LeftClient1.value.size

      _LeftClient1.emit(
        _LeftClient1.value.toMutableList()
          .apply {
            if (_LeftClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient2.value.last()::class)
        _LeftClient2.emit(
          _LeftClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
        popRightClient2()

      val oldSize = _LeftClient2.value.size

      _LeftClient2.emit(
        _LeftClient2.value.toMutableList()
          .apply {
            if (_LeftClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient2(this@run)
      }
    }

    suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient2.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient3.value.last()::class)
        _LeftClient3.emit(
          _LeftClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
        popRightClient3()

      val oldSize = _LeftClient3.value.size

      _LeftClient3.emit(
        _LeftClient3.value.toMutableList()
          .apply {
            if (_LeftClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient3(this@run)
      }
    }

    suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient3.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient4.value.last()::class)
        _LeftClient4.emit(
          _LeftClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
        popRightClient4()

      val oldSize = _LeftClient4.value.size

      _LeftClient4.emit(
        _LeftClient4.value.toMutableList()
          .apply {
            if (_LeftClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient4.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient5.value.last()::class)
        _LeftClient5.emit(
          _LeftClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
        popRightClient5()

      val oldSize = _LeftClient5.value.size

      _LeftClient5.emit(
        _LeftClient5.value.toMutableList()
          .apply {
            if (_LeftClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient5.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient1.value.last()::class)
        _RightClient1.emit(
          _RightClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient1.value.size

      _RightClient1.emit(
        _RightClient1.value.toMutableList()
          .apply {
            if (_RightClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient1(this@run)
      }
    }

    suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient2.value.last()::class)
        _RightClient2.emit(
          _RightClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient2.value.size

      _RightClient2.emit(
        _RightClient2.value.toMutableList()
          .apply {
            if (_RightClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient2(this@run)
      }
    }

    suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient2.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient3.value.last()::class)
        _RightClient3.emit(
          _RightClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient3.value.size

      _RightClient3.emit(
        _RightClient3.value.toMutableList()
          .apply {
            if (_RightClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient3(this@run)
      }
    }

    suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient3.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient4.value.last()::class)
        _RightClient4.emit(
          _RightClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient4.value.size

      _RightClient4.emit(
        _RightClient4.value.toMutableList()
          .apply {
            if (_RightClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient4(this@run)
      }
    }

    suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient4.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient5.value.last()::class)
        _RightClient5.emit(
          _RightClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient5.value.size

      _RightClient5.emit(
        _RightClient5.value.toMutableList()
          .apply {
            if (_RightClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient5(this@run)
      }
    }

    suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient5.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      TransactionSupply.run {
        if (isNarrowScreen) {
          if (RightClient1.value.size > 1) {
            _LeftClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient1.value.subList(1, RightClient1.value.size)
                )
              }
            )
          }
          clearRightClient1()

          if (RightClient2.value.size > 1) {
            _LeftClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient2.value.subList(1, RightClient2.value.size)
                )
              }
            )
          }
          clearRightClient2()

          if (RightClient3.value.size > 1) {
            _LeftClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient3.value.subList(1, RightClient3.value.size)
                )
              }
            )
          }
          clearRightClient3()

          if (RightClient4.value.size > 1) {
            _LeftClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient4.value.subList(1, RightClient4.value.size)
                )
              }
            )
          }
          clearRightClient4()

          if (RightClient5.value.size > 1) {
            _LeftClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient5.value.subList(1, RightClient5.value.size)
                )
              }
            )
          }
          clearRightClient5()

        } else {
          if (LeftClient1.value.size > 1) {
            _RightClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient1.value.subList(1, LeftClient1.value.size)
                )
              }
            )
          }
          clearLeftClient1()

          if (LeftClient2.value.size > 1) {
            _RightClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient2.value.subList(1, LeftClient2.value.size)
                )
              }
            )
          }
          clearLeftClient2()

          if (LeftClient3.value.size > 1) {
            _RightClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient3.value.subList(1, LeftClient3.value.size)
                )
              }
            )
          }
          clearLeftClient3()

          if (LeftClient4.value.size > 1) {
            _RightClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient4.value.subList(1, LeftClient4.value.size)
                )
              }
            )
          }
          clearLeftClient4()

          if (LeftClient5.value.size > 1) {
            _RightClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient5.value.subList(1, LeftClient5.value.size)
                )
              }
            )
          }
          clearLeftClient5()
        }
      }
    }
  }

  object TransactionSupply {

    suspend fun go(
      model: NavigationScreenModel.Transaction,
      isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
      remove: Boolean = false
    ) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> goLeftClient1(model, remove)
          1 -> goLeftClient2(model, remove)
          2 -> goLeftClient3(model, remove)
          3 -> goLeftClient4(model, remove)
          else -> goLeftClient5(model, remove)
        }
      } else {
        when (ClientId.value) {
          0 -> goRightClient1(model, remove)
          1 -> goRightClient2(model, remove)
          2 -> goRightClient3(model, remove)
          3 -> goRightClient4(model, remove)
          else -> goRightClient5(model, remove)
        }
      }
    }

    suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> popLeftClient1(navigateAfterwards)
          1 -> popLeftClient2(navigateAfterwards)
          2 -> popLeftClient3(navigateAfterwards)
          3 -> popLeftClient4(navigateAfterwards)
          else -> popLeftClient5(navigateAfterwards)
        }
      } else {
        when (ClientId.value) {
          0 -> popRightClient1(navigateAfterwards)
          1 -> popRightClient2(navigateAfterwards)
          2 -> popRightClient3(navigateAfterwards)
          3 -> popRightClient4(navigateAfterwards)
          else -> popRightClient5(navigateAfterwards)
        }
      }
    }

    suspend fun clear(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen) {
      if (isNarrowScreen) {
        when (ClientId.value) {
          0 -> clearLeftClient1()
          1 -> clearLeftClient2()
          2 -> clearLeftClient3()
          3 -> clearLeftClient4()
          else -> clearLeftClient5()
        }
      } else {
        when (ClientId.value) {
          0 -> clearRightClient1()
          1 -> clearRightClient2()
          2 -> clearRightClient3()
          3 -> clearRightClient4()
          else -> clearRightClient5()
        }
      }
    }

    fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
      return (
          if (isNarrowScreen)
            when (clientId) {
              0 -> _LeftClient1
              1 -> _LeftClient2
              2 -> _LeftClient3
              3 -> _LeftClient4
              else -> _LeftClient5
            }
          else
            when (clientId) {
              0 -> _RightClient1
              1 -> _RightClient2
              2 -> _RightClient3
              3 -> _RightClient4
              else -> _RightClient5
            }
          ).value.size == 1
    }

    fun isVeryFirstScreenLeft(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _LeftClient1
        1 -> _LeftClient2
        2 -> _LeftClient3
        3 -> _LeftClient4
        else -> _LeftClient5
      }).value.size == 1
    }

    fun isVeryFirstScreenRight(clientId: Int): Boolean {
      return (when (clientId) {
        0 -> _RightClient1
        1 -> _RightClient2
        2 -> _RightClient3
        3 -> _RightClient4
        else -> _RightClient5
      }).value.size == 1
    }

    private val _ClientId =
      MutableStateFlow(0)
    val ClientId =
      _ClientId.asStateFlow()

    suspend fun setClientId(id: Int) {
      _ClientId.emit(id)
    }

    private val _LeftClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient1 =
      _LeftClient1.asStateFlow()

    private val _LeftClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient2 =
      _LeftClient2.asStateFlow()

    private val _LeftClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient3 =
      _LeftClient3.asStateFlow()

    private val _LeftClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient4 =
      _LeftClient4.asStateFlow()

    private val _LeftClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
    val LeftClient5 =
      _LeftClient5.asStateFlow()

    private val _RightClient1 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient1 =
      _RightClient1.asStateFlow()

    private val _RightClient2 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient2 =
      _RightClient2.asStateFlow()

    private val _RightClient3 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient3 =
      _RightClient3.asStateFlow()

    private val _RightClient4 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient4 =
      _RightClient4.asStateFlow()

    private val _RightClient5 =
      MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
    val RightClient5 =
      _RightClient5.asStateFlow()

    suspend fun goLeftClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient1.value.last()::class)
        _LeftClient1.emit(
          _LeftClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
        popRightClient1()

      val oldSize = _LeftClient1.value.size

      _LeftClient1.emit(
        _LeftClient1.value.toMutableList()
          .apply {
            if (_LeftClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient1(this@run)
      }
    }

    suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient1.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient2.value.last()::class)
        _LeftClient2.emit(
          _LeftClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
        popRightClient2()

      val oldSize = _LeftClient2.value.size

      _LeftClient2.emit(
        _LeftClient2.value.toMutableList()
          .apply {
            if (_LeftClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient2(this@run)
      }
    }

    suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient2.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient3.value.last()::class)
        _LeftClient3.emit(
          _LeftClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
        popRightClient3()

      val oldSize = _LeftClient3.value.size

      _LeftClient3.emit(
        _LeftClient3.value.toMutableList()
          .apply {
            if (_LeftClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient3(this@run)
      }
    }

    suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient3.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient4.value.last()::class)
        _LeftClient4.emit(
          _LeftClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
        popRightClient4()

      val oldSize = _LeftClient4.value.size

      _LeftClient4.emit(
        _LeftClient4.value.toMutableList()
          .apply {
            if (_LeftClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient4(this@run)
      }
    }

    suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient4.emit(
        listOf(model)
      )
    }

    suspend fun goLeftClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _LeftClient5.value.last()::class)
        _LeftClient5.emit(
          _LeftClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
        popRightClient5()

      val oldSize = _LeftClient5.value.size

      _LeftClient5.emit(
        _LeftClient5.value.toMutableList()
          .apply {
            if (_LeftClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_LeftClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goLeftClient5(this@run)
      }
    }

    suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
      _LeftClient5.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient1(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient1.value.last()::class)
        _RightClient1.emit(
          _RightClient1
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient1.value.size

      _RightClient1.emit(
        _RightClient1.value.toMutableList()
          .apply {
            if (_RightClient1.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient1.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient1(this@run)
      }
    }

    suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient1.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient2(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient2.value.last()::class)
        _RightClient2.emit(
          _RightClient2
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient2.value.size

      _RightClient2.emit(
        _RightClient2.value.toMutableList()
          .apply {
            if (_RightClient2.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient2.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient2(this@run)
      }
    }

    suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient2.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient3(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient3.value.last()::class)
        _RightClient3.emit(
          _RightClient3
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient3.value.size

      _RightClient3.emit(
        _RightClient3.value.toMutableList()
          .apply {
            if (_RightClient3.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient3.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient3(this@run)
      }
    }

    suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient3.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient4(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient4.value.last()::class)
        _RightClient4.emit(
          _RightClient4
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient4.value.size

      _RightClient4.emit(
        _RightClient4.value.toMutableList()
          .apply {
            if (_RightClient4.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient4.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient4(this@run)
      }
    }

    suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient4.emit(
        listOf(model)
      )
    }

    suspend fun goRightClient5(
      model: NavigationScreenModel.Transaction,
      remove: Boolean = false
    ) {
      if (model::class != _RightClient5.value.last()::class)
        _RightClient5.emit(
          _RightClient5
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
      val oldSize = _RightClient5.value.size

      _RightClient5.emit(
        _RightClient5.value.toMutableList()
          .apply {
            if (_RightClient5.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_RightClient5.value.size == oldSize)
          delay(30)

        delay(300)

        goRightClient5(this@run)
      }
    }

    suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
      _RightClient5.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      TransactionSupply.run {
        if (isNarrowScreen) {
          if (RightClient1.value.size > 1) {
            _LeftClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient1.value.subList(1, RightClient1.value.size)
                )
              }
            )
          }
          clearRightClient1()

          if (RightClient2.value.size > 1) {
            _LeftClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient2.value.subList(1, RightClient2.value.size)
                )
              }
            )
          }
          clearRightClient2()

          if (RightClient3.value.size > 1) {
            _LeftClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient3.value.subList(1, RightClient3.value.size)
                )
              }
            )
          }
          clearRightClient3()

          if (RightClient4.value.size > 1) {
            _LeftClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient4.value.subList(1, RightClient4.value.size)
                )
              }
            )
          }
          clearRightClient4()

          if (RightClient5.value.size > 1) {
            _LeftClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Cart)
                addAll(
                  RightClient5.value.subList(1, RightClient5.value.size)
                )
              }
            )
          }
          clearRightClient5()

        } else {
          if (LeftClient1.value.size > 1) {
            _RightClient1.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient1.value.subList(1, LeftClient1.value.size)
                )
              }
            )
          }
          clearLeftClient1()

          if (LeftClient2.value.size > 1) {
            _RightClient2.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient2.value.subList(1, LeftClient2.value.size)
                )
              }
            )
          }
          clearLeftClient2()

          if (LeftClient3.value.size > 1) {
            _RightClient3.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient3.value.subList(1, LeftClient3.value.size)
                )
              }
            )
          }
          clearLeftClient3()

          if (LeftClient4.value.size > 1) {
            _RightClient4.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient4.value.subList(1, LeftClient4.value.size)
                )
              }
            )
          }
          clearLeftClient4()

          if (LeftClient5.value.size > 1) {
            _RightClient5.emit(
              mutableListOf<NavigationScreenModel.Transaction>().apply {
                add(NavigationScreenModel.Transaction.Selection)
                addAll(
                  LeftClient5.value.subList(1, LeftClient5.value.size)
                )
              }
            )
          }
          clearLeftClient5()
        }
      }
    }
  }

  object Stock {

    fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
      return (if (isNarrowScreen) _Left else _Right).value.size == 1
    }

    fun isVeryFirstScreenLeft(): Boolean {
      return _Left.value.size == 1
    }

    fun isVeryFirstScreenRight(): Boolean {
      return _Right.value.size == 1
    }

    private val _Left =
      MutableStateFlow<List<NavigationScreenModel.Stock>>(listOf(NavigationScreenModel.Stock.Warehouse))
    val Left =
      _Left.asStateFlow()

    private val _Right =
      MutableStateFlow<List<NavigationScreenModel.Stock>>(listOf(NavigationScreenModel.Stock.AddEditGoodsItem))
    val Right =
      _Right.asStateFlow()

    suspend fun go(
      model: NavigationScreenModel.Stock,
      isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
      remove: Boolean = false,
      forceSecond: Boolean = false
    ) {
      if (isNarrowScreen)
        goLeft(model, remove, forceSecond)
      else
        goRight(model, remove, forceSecond)
    }

    suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Stock? = null) {
      if (isNarrowScreen)
        popLeft(navigateAfterwards)
      else
        popRight(navigateAfterwards)
    }

    suspend fun goLeft(
      model: NavigationScreenModel.Stock,
      remove: Boolean = false,
      forceSecond: Boolean = false
    ) {
      if (model::class != _Left.value.last()::class || forceSecond)
        _Left.emit(
          _Left
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeft(navigateAfterwards: NavigationScreenModel.Stock? = null) {
      if (_Right.value.last()::class == _Left.value.last()::class)
        popRight()

      val oldSize = _Left.value.size

      _Left.emit(
        _Left.value.toMutableList()
          .apply {
            if (_Left.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Left.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearLeft(model: NavigationScreenModel.Stock = NavigationScreenModel.Stock.Warehouse) {
      _Left.emit(
        listOf(model)
      )
    }

    suspend fun goRight(
      model: NavigationScreenModel.Stock,
      remove: Boolean = false,
      forceSecond: Boolean = false
    ) {
      if (model::class != _Right.value.last()::class || forceSecond) {
        _Right.emit(
          _Right
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
      }
    }

    suspend fun popRight(navigateAfterwards: NavigationScreenModel.Stock? = null) {
      val oldSize = _Right.value.size

      _Right.emit(
        _Right.value.toMutableList()
          .apply {
            if (_Right.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Right.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearRight(model: NavigationScreenModel.Stock = NavigationScreenModel.Stock.AddEditGoodsItem) {
      _Right.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      Stock.run {
        if (isNarrowScreen) {
          if (Right.value.size > 1) {
            _Left.emit(
              mutableListOf<NavigationScreenModel.Stock>().apply {
                add(NavigationScreenModel.Stock.Warehouse)
                addAll(Right.value.subList(1, Right.value.size))
              }
            )
          }
          clearRight()
        } else {
          if (_Left.value.size > 1) {
            _Right.emit(
              mutableListOf<NavigationScreenModel.Stock>().apply {
                add(NavigationScreenModel.Stock.AddEditGoodsItem)
                addAll(_Left.value.subList(1, _Left.value.size))
              }
            )
          }
          clearLeft()
        }
      }
    }
  }

  object Menu {

    val listScreens = listOf(
      NavigationScreenModel.Menu.AppMode,
      NavigationScreenModel.Menu.UserAccount,
      NavigationScreenModel.Menu.Finances,
      NavigationScreenModel.Menu.Stores,
      NavigationScreenModel.Menu.TransactionHistory,
      NavigationScreenModel.Menu.Analytics,
      NavigationScreenModel.Menu.Workers,
      NavigationScreenModel.Menu.Suppliers,
      NavigationScreenModel.Menu.Debtors,
      NavigationScreenModel.Menu.Devices,
      NavigationScreenModel.Menu.AppLanguage,
      NavigationScreenModel.Menu.AppTheme
    )

    private val _Left =
      MutableStateFlow<List<NavigationScreenModel.Menu>>(listOf(NavigationScreenModel.Menu.List))
    val Left =
      _Left.asStateFlow()

    fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
      return (if (isNarrowScreen) _Left else _Right).value.size == 1
    }

    fun isVeryFirstScreenLeft(): Boolean {
      return _Left.value.size == 1
    }

    fun isVeryFirstScreenRight(): Boolean {
      return _Right.value.size == 1
    }

    private val _Right =
      MutableStateFlow<List<NavigationScreenModel.Menu>>(listOf(NavigationScreenModel.Menu.UserAccount))
    val Right =
      _Right.asStateFlow()

    suspend fun go(
      model: NavigationScreenModel.Menu,
      isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
      remove: Boolean = false,
      forceSecond: Boolean = false
    ) {
      if (isNarrowScreen)
        goLeft(model, remove, forceSecond)
      else
        goRight(model, remove, forceSecond)
    }

    suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Menu? = null) {
      if (isNarrowScreen)
        popLeft(navigateAfterwards)
      else
        popRight(navigateAfterwards)
    }

    suspend fun goLeft(
      model: NavigationScreenModel.Menu,
      remove: Boolean = false,
      forceSecond: Boolean = false
    ) {
      if (model::class != _Left.value.last()::class || forceSecond)
        _Left.emit(
          _Left
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeft(navigateAfterwards: NavigationScreenModel.Menu? = null) {
      if (_Right.value.last()::class == _Left.value.last()::class)
        popRight()

      val oldSize = _Left.value.size

      _Left.emit(
        _Left.value.toMutableList()
          .apply {
            if (_Left.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Left.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearLeft(model: NavigationScreenModel.Menu = NavigationScreenModel.Menu.List) {
      _Left.emit(
        listOf(model)
      )
    }

    suspend fun goRight(
      model: NavigationScreenModel.Menu,
      remove: Boolean = false,
      forceSecond: Boolean = false
    ) {
      if (model::class != _Right.value.last()::class || forceSecond)
        _Right.emit(
          _Right
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRight(navigateAfterwards: NavigationScreenModel.Menu? = null) {
      val oldSize = _Right.value.size

      _Right.emit(
        _Right.value.toMutableList()
          .apply {
            if (_Right.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Right.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearRight(model: NavigationScreenModel.Menu = NavigationScreenModel.Menu.UserAccount) {
      _Right.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      Menu.run {
        if (isNarrowScreen) {
          if (Right.value.size > 1) {
            _Left.emit(
              mutableListOf<NavigationScreenModel.Menu>().apply {
                add(NavigationScreenModel.Menu.List)
                addAll(Right.value.subList(1, Right.value.size))
              }
            )
          }
          clearRight()
        } else {
          if (_Left.value.size > 1) {
            _Right.emit(
              mutableListOf<NavigationScreenModel.Menu>().apply {
                add(NavigationScreenModel.Menu.UserAccount)
                addAll(_Left.value.subList(1, _Left.value.size))
              }
            )
          }
          clearLeft()
        }
      }
    }
  }

  object UserAuth {

    fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
      return (if (isNarrowScreen) _Left else _Right).value.size == 1
    }

    fun isVeryFirstScreenLeft(): Boolean {
      return _Left.value.size == 1
    }

    fun isVeryFirstScreenRight(): Boolean {
      return _Right.value.size == 1
    }

    private val _Left =
      MutableStateFlow<List<NavigationScreenModel.UserAuth>>(listOf(NavigationScreenModel.UserAuth.LogIn))
    val Left =
      _Left.asStateFlow()

    private val _Right =
      MutableStateFlow<List<NavigationScreenModel.UserAuth>>(listOf(NavigationScreenModel.UserAuth.SignUp))
    val Right =
      _Right.asStateFlow()

    suspend fun goLeft(
      model: NavigationScreenModel.UserAuth,
      remove: Boolean = false
    ) {
      if (model::class != _Left.value.last()::class)
        _Left.emit(
          _Left
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popLeft(navigateAfterwards: NavigationScreenModel.UserAuth? = null) {
      if (_Right.value.last()::class == _Left.value.last()::class)
        popRight()

      val oldSize = _Left.value.size

      _Left.emit(
        _Left.value.toMutableList()
          .apply {
            if (_Left.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Left.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearLeft(model: NavigationScreenModel.UserAuth = NavigationScreenModel.UserAuth.LogIn) {
      _Left.emit(
        listOf(model)
      )
    }

    suspend fun goRight(
      model: NavigationScreenModel.UserAuth,
      remove: Boolean = false
    ) {
      if (model::class != _Right.value.last()::class)
        _Right.emit(
          _Right
            .value.toMutableList()
            .apply {
              if (remove)
                removeAt(lastIndex)

              add(model)
            }
        )
    }

    suspend fun popRight(navigateAfterwards: NavigationScreenModel.UserAuth? = null) {
      val oldSize = _Right.value.size

      _Right.emit(
        _Right.value.toMutableList()
          .apply {
            if (_Right.value.size > 1)
              removeAt(lastIndex)
          }
      )

      navigateAfterwards?.run {
        while (_Right.value.size == oldSize)
          delay(30)

        delay(300)

        goLeft(this@run)
      }
    }

    suspend fun clearRight(model: NavigationScreenModel.UserAuth = NavigationScreenModel.UserAuth.SignUp) {
      _Right.emit(
        listOf(model)
      )
    }

    suspend fun init(isNarrowScreen: Boolean) {
      UserAuth.run {
        if (isNarrowScreen) {
          if (Right.value.size > 1) {
            _Left.emit(
              mutableListOf<NavigationScreenModel.UserAuth>().apply {
                add(NavigationScreenModel.UserAuth.LogIn)
                addAll(Right.value.subList(1, Right.value.size))
              }
            )
          }
          clearRight()
        } else {
          if (Left.value.size > 1) {
            _Right.emit(
              mutableListOf<NavigationScreenModel.UserAuth>().apply {
                add(NavigationScreenModel.UserAuth.SignUp)
                addAll(_Left.value.subList(1, _Left.value.size))
              }
            )
          }
          clearLeft()
        }
      }
    }
  }
}

@Composable
fun AppConfiguration.ModalDialogWidget(
  title: String,
  subTitle: String? = null,
  negativeButtonText: String = stateValues.stringCancel,
  positiveButtonText: String = stateValues.stringConfirm,
  backgroundColor: Color = stateValues.BackgroundColor,
  cornerRadius: Dp = stateValues.cornerRadius,
  titleTextSize: TextUnit = stateValues.accentTextSize,
  subTitleTextSize: TextUnit = stateValues.textSize,
  titleTextColor: Color = stateValues.TextColor,
  subTitleTextColor: Color = titleTextColor,
  onDismiss: () -> Unit,
  negativeAction: () -> Unit,
  positiveAction: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
  ) {
    Column(
      modifier = Modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(backgroundColor)
        .fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = title,
        modifier = Modifier,
        fontSize = titleTextSize,
        color = titleTextColor,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      subTitle?.run {
        Text(
          text = subTitle,
          fontSize = subTitleTextSize,
          color = subTitleTextColor,
          textAlign = TextAlign.Center
        )
      }

      Row(
        modifier = Modifier
          .padding(horizontal = 16.dp)
      ) {
        actionButton(
          modifier = Modifier
            .weight(1f),
          enabledColor = Color.Transparent,
          text = negativeButtonText,
          onClick = negativeAction
        )

        Spacer(
          modifier = Modifier
            .width(4.dp)
        )

        actionButton(
          modifier = Modifier
            .weight(1f),
          text = positiveButtonText,
          onClick = negativeAction
        )
      }
    }
  }
}

@Composable
fun AppConfiguration.MessageText(
  modifier: Modifier = Modifier,
  text: String,
  subText: String? = null,
  textColor: Color = stateValues.TextColor,
  textSize: TextUnit = stateValues.accentTextSize,
  subTextColor: Color = textColor,
  subTextSize: TextUnit = stateValues.textSize
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = text,
      modifier = Modifier
        .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = if (subText == null) 24.dp else 0.dp),
      color = textColor,
      fontSize = textSize,
      fontWeight = FontWeight.Bold
    )

    subText?.run {
      Text(
        text = this,
        modifier = Modifier
          .padding(start = 24.dp, bottom = 24.dp, end = 24.dp),
        color = subTextColor,
        fontSize = subTextSize
      )
    }
  }
}

@Composable
fun AppConfiguration.MenuWorkersScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringWorkers,
      iconPath = stateValues.drawablePathIconWorkers,
      trailingIcons = listOf(
        Triple(
          stateValues.drawablePathIconPerson,
          stateValues.drawableResIconPerson.value
        ) {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditWorker, stateValues.isNarrowScreen)
          }
        },
        Triple(
          stateValues.drawablePathIconAdd,
          stateValues.drawableResIconAdd.value
        ) {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditWorker, stateValues.isNarrowScreen)
          }
        }
      ),
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}

@Composable
fun AppConfiguration.MenuUserAccountScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringUserAccount,
      iconPath = stateValues.drawablePathIconUserAccount,
      trailingIcons = listOf(
        Triple(
          stateValues.drawablePathIconExit,
          stateValues.drawableResIconExit.value
        ) {
          logOutUser()
        },
      ),
      onBack = if (!Navigation.Menu.isVeryFirstScreen(stateValues.isNarrowScreen)) {
        {
          coroutineScope.launch {
            Navigation.Menu.pop(stateValues.isNarrowScreen)
          }
        }
      } else null
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxHeight()
        .fillMaxWidth(0.5f)
        .padding(vertical = 24.dp)
    ) {
      item {
        val outerSpace = 16.dp
        val innerSpace = 8.dp

        val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
          valueInitial = stateValues.userAccount?.phoneNumber,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val emailTextFieldContent = emailTextField(
          valueInitial = stateValues.userAccount?.email,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_EMAIL
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val firstNameTextFieldContent = genericTextField(
          valueInitial = stateValues.userAccount?.firstName,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_FIRST_NAME,
          titleText = stateValues.stringFirstName,
          placeholderText = stateValues.stringEnterFirstName,
          leadingIconPath = stateValues.drawablePathIconPerson,
          contentInvalidText = stateValues.stringFirstNameCannotBeEmptyOrJustWhitespaces,
          onContentValidityCheck = {
            it.checkAsPersonName()
          },
          onFilterValue = {
            it.filterAsPersonName()
          }
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val lastNameTextFieldContent = genericTextField(
          valueInitial = stateValues.userAccount?.lastName,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_LAST_NAME,
          titleText = stateValues.stringLastName,
          placeholderText = stateValues.stringEnterLastName,
          leadingIconPath = stateValues.drawablePathIconPerson,
          contentInvalidText = stateValues.stringLastNameCannotBeEmptyOrJustWhitespaces,
          onContentValidityCheck = {
            it.checkAsPersonName()
          },
          onFilterValue = {
            it.filterAsPersonName()
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        Text(
          text = stateValues.stringChangePassword,
          color = stateValues.TextColor,
          fontSize = stateValues.titleTextSize,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val (passwordTextFieldContent: GenericTextFieldContent?, repeatedPasswordTextFieldContent: GenericTextFieldContent?)  = repeatedPasswordTextFieldGroup(
          passwordTitleText = stateValues.stringNewPassword,
          passwordPlaceholderText = stateValues.stringEnterNewPassword,
          repeatPasswordTitleText = stateValues.stringRepeatNewPassword,
          repeatPasswordPlaceholderText = stateValues.stringRepeatNewPassword,
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_PASSWORD,
          repeatedStateKey = NavigationScreenModel.KEY_STATE_REPEATED_PASSWORD,
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        var goAction: (() -> Unit)? = null

        val confirmationPasswordTextFieldContent: GenericTextFieldContent? = passwordTextField(
          titleText = stateValues.stringConfirmationPassword,
          placeholderText = stateValues.stringRequiredToEditAccount,
          contentInvalidText = stateValues.stringRequiredToEditAccount + ". \n" + stateValues.stringPasswordMustBe,
          imeWithAction = ImeWithAction(ImeAction.Go) {
            goAction?.invoke()
          },
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.Menu.UserAccount.KEY_STATE_CONFIRMATION_PASSWORD
        )

//        responseText(
//          stateValues.stringUserWithThisPhoneNumberIsAlreadyRegistered,
//          showIf = {
//            (stateValues.userAccountState as? DataState.Failure)?.message?.equals(
//              stateValues.exceptionMessageUserWithThisPhoneNumberIsAlreadyRegistered,
//              true
//            ) == true
//          }
//        )
//
//        responseText(
//          stateValues.stringUserWithThisEmailAddressIsAlreadyRegistered,
//          showIf = {
//            (stateValues.userAccountState as? DataState.Failure)?.message?.equals(
//              stateValues.exceptionMessageUserWithThisEmailAddressIsAlreadyRegistered,
//              true
//            ) == true
//          }
//        )
//
//        responseText(
//          stateValues.stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered,
//          showIf = {
//            (stateValues.userAccountState as? DataState.Failure)?.message?.equals(
//              stateValues.exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered,
//              true
//            ) == true
//          }
//        )

        Spacer(modifier = Modifier.height(outerSpace))

        goAction = {
          softKeyboardController?.hide()

          phoneNumberTextFieldContent.checkContentValidity()
          emailTextFieldContent.checkContentValidity()

          firstNameTextFieldContent.checkContentValidity()
          lastNameTextFieldContent.checkContentValidity()

          if (passwordTextFieldContent!!.value.text.isNotEmpty())
            passwordTextFieldContent.checkContentValidity()

          if (passwordTextFieldContent.value.text.isNotEmpty())
            repeatedPasswordTextFieldContent!!.checkContentValidity()

          confirmationPasswordTextFieldContent!!.checkContentValidity()

          if (
            phoneNumberTextFieldContent.isContentValid
            && emailTextFieldContent.isContentValid
            && firstNameTextFieldContent.isContentValid
            && lastNameTextFieldContent.isContentValid
            && (passwordTextFieldContent.value.text.isEmpty() || passwordTextFieldContent.isContentValid)
            && (passwordTextFieldContent.value.text.isEmpty() || repeatedPasswordTextFieldContent!!.isContentValid)
            && confirmationPasswordTextFieldContent.isContentValid
          ) {
            updateUser(
              userAccountUpdate = UserAccountUpdateDataModel(
                account = UserAccountDataModel(
                  id = "",
                  phoneNumber = stateValues.globalAppConfiguration.countries.run {
                    find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                  }.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase(),
                  email = emailTextFieldContent.value.text.trim().lowercase(),
                  firstName = firstNameTextFieldContent.value.text.trim(),
                  lastName = lastNameTextFieldContent.value.text.trim(),
                  countryLocale = phoneNumberTextFieldContent.selectedId,
                  workerAccountIds = stateValues.userAccount?.workerAccountIds,
                  supplierAccountIds = stateValues.userAccount?.supplierAccountIds,
                  createdAt = 0L,
                  isActive = true
                ),
                password = confirmationPasswordTextFieldContent!!.value.text,
                newPassword = passwordTextFieldContent!!.takeIf { it.value.text.isNotEmpty() }?.value?.text
              )
            )

            confirmationPasswordTextFieldContent.reset()
            passwordTextFieldContent.reset()
            repeatedPasswordTextFieldContent?.reset()
          }
        }

        actionButton(
          text = stateValues.stringEdit,
          enabled = stateValues.latestNotification == null
        ) {
          goAction.invoke()
        }

        Spacer(
          modifier = Modifier
            .height(stateValues.screenHeight / 10)
        )
      }
    }
  }
}

@Composable
fun AppConfiguration.MenuTransactionHistoryScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringTransactionHistory,
      iconPath = stateValues.drawablePathIconTransactionHistory,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}

@Composable
fun MenuTransactionHistoryReceiptPreviewScreen() {
}

@Composable
fun AppConfiguration.MenuSuppliersScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringSuppliers,
      iconPath = stateValues.drawablePathIconSuppliers,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}

@Composable
fun MenuStoreSubscriptionPlansScreen() {
}

@Composable
fun AppConfiguration.MenuStoresScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringStores,
      iconPath = stateValues.drawablePathIconStores,
//      leadingContent = stateValues.activeStoreId?.let { activeStoreId ->
//        stateValues.stores?.find { it.id == activeStoreId }?.let { activeStore ->
//          activeStore.name.extractLocalizedString(stateValues.appLanguage)?.let { name ->
//            {
//              Row(
//                horizontalArrangement = Arrangement.Center
//              ) {
//                Text(
//                  text = name,
//                  color = stateValues.TextColor,
//                  fontSize = stateValues.accentTextSize,
//                  fontWeight = FontWeight.Bold
//                )
//
//                Spacer(modifier = Modifier.width(8.dp))
//
//                actionButton(
//                  text = "",
//                  iconPath = stateValues.drawablePathIconSwitch
//                ) {
//
//                }
//              }
//            }
//          }
//        }
//      },
      trailingIcons = listOf(
        Triple(
          stateValues.drawablePathIconAdd,
          stateValues.drawableResIconAdd.value
        ) {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore, stateValues.isNarrowScreen)
          }
        },
      ),
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    Column(
      modifier = Modifier
        .fillMaxWidth(0.6f)
        .fillMaxHeight()
    ) {
      when (val state = stateValues.storesState) {
        is DataState.Success -> {
          if (state.payload.isEmpty()) {
            MessageText(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              stateValues.stringListEmpty
            )
          } else {
            val searchTextFieldContent: GenericTextFieldContent? =
              searchTextField(
                stateHost = NavigationScreenModel.Menu.Stores,
                stateKey = NavigationScreenModel.KEY_STATE_EMAIL,
                modifier = Modifier
                  .padding(top = 8.dp)
              )

            stateValues.activeStoreId?.run {
              stateValues.stores?.find {
                stateValues.activeStoreId == it.id
              }?.let { store ->
                StoreWidget(
                  modifier = Modifier
                    .padding(top = 8.dp),
                  store = store,
                  onDelete = {

                  },
                  onEdit = {
                    coroutineScope.launch {
                      NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID to store.id)
                      Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                    }
                  },
                  onSetInactive = {
                    setActiveStoreId(null)
                  }
                )
              }
            }

            val items = searchTextFieldContent!!.value
              .text
              .takeIf {
                it.isNotEmpty()
              }?.let { query ->
                state.payload.search<StoreDataModel>(query).first
              } ?: state.payload

            if (items.isEmpty()) {
              MessageText(
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f),
                stateValues.stringNoMatches
              )
            } else {
              LazyColumn(
                modifier = Modifier
                  .weight(1f)
                  .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                items(items) { store ->
                  val isActive = store.id == stateValues.activeStoreId

                  if (!isActive)
                    StoreWidget(
                      store = store,
                      onDelete = {

                      },
                      onEdit = {
                        coroutineScope.launch {
                          NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID to store.id)
                          Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                        }
                      },
                      onSetActive = if (isActive) null else {
                        {
                          setActiveStoreId(it.id)
                        }
                      }
                    )
                }

                item {
                  Spacer(
                    modifier = Modifier
                      .height(stateValues.screenHeight / 4)
                  )
                }
              }
            }
          }
        }

        is DataState.Empty -> {
          MessageText(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            stateValues.stringListEmpty
          )
        }
      }
    }
  }
}

@Composable
fun AppConfiguration.MenuScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {

    if (stateValues.isNarrowScreen) {
      AnimatedContent(
        modifier = Modifier
          .weight(1f),
        targetState = stateValues.navigationScreensMenuLeft.last()
      ) { model ->
        when (model) {
          is NavigationScreenModel.Menu.List -> {
            MenuListScreen()
          }
          is NavigationScreenModel.Menu.AppMode -> {
            MenuAppModeScreen()
          }
          is NavigationScreenModel.Menu.UserAccount -> {
            MenuUserAccountScreen()
          }
          is NavigationScreenModel.Menu.Finances -> {
            MenuFinancesScreen()
          }
          is NavigationScreenModel.Menu.GoodsCategories -> {
            MenuGoodsCategoriesScreen()
          }
          is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
            MenuAddEditGoodsCategoryScreen()
          }
          is NavigationScreenModel.Menu.Stores -> {
            MenuStoresScreen()
          }
          is NavigationScreenModel.Menu.AddEditStore -> {
            MenuAddEditStoreScreen()
          }
          is NavigationScreenModel.Menu.StoreSubscription -> {
            MenuStoreSubscriptionPlansScreen()
          }
          is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
            MenuStoreSubscriptionPlansScreen()
          }
          is NavigationScreenModel.Menu.TransactionHistory -> {
            MenuTransactionHistoryScreen()
          }
          is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
            MenuTransactionHistoryReceiptPreviewScreen()
          }
          is NavigationScreenModel.Menu.Analytics -> {
            MenuAnalyticsScreen()
          }
          is NavigationScreenModel.Menu.Workers -> {
            MenuWorkersScreen()
          }
          is NavigationScreenModel.Menu.AddEditWorker -> {
            MenuAddEditWorkerScreen()
          }
          is NavigationScreenModel.Menu.Suppliers -> {
            MenuSuppliersScreen()
          }
          is NavigationScreenModel.Menu.AddEditSupplier -> {
            MenuAddEditSupplierScreen()
          }
          is NavigationScreenModel.Menu.Debtors -> {
            MenuDebtorsScreen()
          }
          is NavigationScreenModel.Menu.CloseDebt -> {
            MenuCloseDebtScreen()
          }
          is NavigationScreenModel.Menu.Devices -> {
            MenuDevicesScreen()
          }
          is NavigationScreenModel.Menu.AppLanguage -> {
            MenuAppLanguageScreen()
          }
          is NavigationScreenModel.Menu.AppTheme -> {
            MenuAppThemeScreen()
          }

          else -> {}
        }
      }
    } else {
      Row(
        modifier = Modifier
          .weight(1f)
      ) {
        AnimatedContent(
          modifier = Modifier
            .weight(0.2f),
          targetState = stateValues.navigationScreensMenuLeft.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Menu.List -> {
              MenuListScreen()
            }
            is NavigationScreenModel.Menu.AppMode -> {
              MenuAppModeScreen()
            }
            is NavigationScreenModel.Menu.UserAccount -> {
              MenuUserAccountScreen()
            }
            is NavigationScreenModel.Menu.Finances -> {
              MenuFinancesScreen()
            }
            is NavigationScreenModel.Menu.GoodsCategories -> {
              MenuGoodsCategoriesScreen()
            }
            is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
              MenuAddEditGoodsCategoryScreen()
            }
            is NavigationScreenModel.Menu.Stores -> {
              MenuStoresScreen()
            }
            is NavigationScreenModel.Menu.AddEditStore -> {
              MenuAddEditStoreScreen()
            }
            is NavigationScreenModel.Menu.StoreSubscription -> {
              MenuStoreSubscriptionPlansScreen()
            }
            is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
              MenuStoreSubscriptionPlansScreen()
            }
            is NavigationScreenModel.Menu.TransactionHistory -> {
              MenuTransactionHistoryScreen()
            }
            is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
              MenuTransactionHistoryReceiptPreviewScreen()
            }
            is NavigationScreenModel.Menu.Analytics -> {
              MenuAnalyticsScreen()
            }
            is NavigationScreenModel.Menu.Workers -> {
              MenuWorkersScreen()
            }
            is NavigationScreenModel.Menu.AddEditWorker -> {
              MenuAddEditWorkerScreen()
            }
            is NavigationScreenModel.Menu.Suppliers -> {
              MenuSuppliersScreen()
            }
            is NavigationScreenModel.Menu.AddEditSupplier -> {
              MenuAddEditSupplierScreen()
            }
            is NavigationScreenModel.Menu.Debtors -> {
              MenuDebtorsScreen()
            }
            is NavigationScreenModel.Menu.CloseDebt -> {
              MenuCloseDebtScreen()
            }
            is NavigationScreenModel.Menu.Devices -> {
              MenuDevicesScreen()
            }
            is NavigationScreenModel.Menu.AppLanguage -> {
              MenuAppLanguageScreen()
            }
            is NavigationScreenModel.Menu.AppTheme -> {
              MenuAppThemeScreen()
            }

            else -> {}
          }
        }

        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = stateValues.navigationScreensMenuRight.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Menu.List -> {
              MenuListScreen()
            }
            is NavigationScreenModel.Menu.AppMode -> {
              MenuAppModeScreen()
            }
            is NavigationScreenModel.Menu.UserAccount -> {
              MenuUserAccountScreen()
            }
            is NavigationScreenModel.Menu.Finances -> {
              MenuFinancesScreen()
            }
            is NavigationScreenModel.Menu.GoodsCategories -> {
              MenuGoodsCategoriesScreen()
            }
            is NavigationScreenModel.Menu.AddEditGoodsCategory -> {
              MenuAddEditGoodsCategoryScreen()
            }
            is NavigationScreenModel.Menu.Stores -> {
              MenuStoresScreen()
            }
            is NavigationScreenModel.Menu.AddEditStore -> {
              MenuAddEditStoreScreen()
            }
            is NavigationScreenModel.Menu.StoreSubscription -> {
              MenuStoreSubscriptionPlansScreen()
            }
            is NavigationScreenModel.Menu.StoreSubscriptionPlans -> {
              MenuStoreSubscriptionPlansScreen()
            }
            is NavigationScreenModel.Menu.TransactionHistory -> {
              MenuTransactionHistoryScreen()
            }
            is NavigationScreenModel.Menu.TransactionHistoryReceiptPreview -> {
              MenuTransactionHistoryReceiptPreviewScreen()
            }
            is NavigationScreenModel.Menu.Analytics -> {
              MenuAnalyticsScreen()
            }
            is NavigationScreenModel.Menu.Workers -> {
              MenuWorkersScreen()
            }
            is NavigationScreenModel.Menu.AddEditWorker -> {
              MenuAddEditWorkerScreen()
            }
            is NavigationScreenModel.Menu.Suppliers -> {
              MenuSuppliersScreen()
            }
            is NavigationScreenModel.Menu.AddEditSupplier -> {
              MenuAddEditSupplierScreen()
            }
            is NavigationScreenModel.Menu.Debtors -> {
              MenuDebtorsScreen()
            }
            is NavigationScreenModel.Menu.CloseDebt -> {
              MenuCloseDebtScreen()
            }
            is NavigationScreenModel.Menu.Devices -> {
              MenuDevicesScreen()
            }
            is NavigationScreenModel.Menu.AppLanguage -> {
              MenuAppLanguageScreen()
            }
            is NavigationScreenModel.Menu.AppTheme -> {
              MenuAppThemeScreen()
            }

            else -> {}
          }
        }

//        LazyColumn(
//          modifier = Modifier
//            .weight(0.5f)
//            .background(stateValues.DisabledColor)
//            .fillMaxHeight()
//        ) {
//
//        }
      }
    }
  }
}

@Composable
fun AppConfiguration.MenuListScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringMenu,
      iconPath = stateValues.drawablePathIconMenu
    )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
    ) {
      items(Navigation.Menu.listScreens) { model ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(
              interactionSource = remember {
                MutableInteractionSource()
              },
              indication = ripple(color = stateValues.TextColor)
            ) {
              coroutineScope.launch {
                Navigation.Menu.go(model, stateValues.isNarrowScreen)
              }
            },
          verticalAlignment = Alignment.CenterVertically
        ) {
          val isActive = stateValues.run {
            if (isNarrowScreen)
              navigationScreensMenuLeft
            else
              navigationScreensMenuRight
          }.last().route == model.route

          CpImage(
            modifier = Modifier
              .padding(8.dp)
              .aspectRatio(1f, matchHeightConstraintsFirst = true),
            url = model.iconPath,
            fallbackRes = model.iconRes,
            contentDescription = stateValues.stringBack,
            tintColor = if (isActive)
              stateValues.AccentColor
            else null
          )

          Text(
            text = model.name,
            color = if (isActive)
              stateValues.AccentColor
            else
              stateValues.TextColor,
            fontWeight = if (isActive)
              FontWeight.Bold
            else
              FontWeight.Normal
          )
        }
      }
    }
  }
}

@Composable
fun AppConfiguration.MenuGoodsCategoriesScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringGoodsCategories,
      iconPath = stateValues.drawablePathIconGoodsCategories,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}

@Composable
fun AppConfiguration.MenuFinancesScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringFinances,
      iconPath = stateValues.drawablePathIconFinances,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}

@Composable
fun AppConfiguration.MenuDevicesScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringDevices,
      iconPath = stateValues.drawablePathIconDevices,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}

@Composable
fun AppConfiguration.MenuDebtorsScreen() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringDebtors,
      iconPath = stateValues.drawablePathIconDebtors,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    val storeId = stateValues.activeStoreId
    val debtors by debtorsState.payload.collectAsState()

    LaunchedEffect(storeId) {
      storeId?.let { getDebtors(it) }
    }

    var search by rememberSaveable { mutableStateOf("") }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val shownDebtors = debtors.orEmpty()
      .filter { it.debtAmount > 0.0 }
      .filter { debtor ->
        val q = search.trim()
        q.isBlank() ||
            debtor.firstName.contains(q, true) ||
            debtor.lastName.contains(q, true) ||
            debtor.phoneNumber.contains(q, true) ||
            debtor.email.contains(q, true)
      }
      .sortedByDescending { it.debtAmount }

    Column(
      modifier = Modifier
        .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
        .padding(horizontal = stateValues.marginTextField)
    ) {
      TransactionPlainTextField(
        title = stateValues.stringSearchByAnyData,
        value = search,
        placeholder = stateValues.stringSearchByAnyData,
        onValueChange = { search = it }
      )
    }

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
        .padding(stateValues.marginTextField)
    ) {
      if (storeId == null) {
        item {
          MessageText(text = stateValues.stringNoActiveStore)
        }
      } else if (shownDebtors.isEmpty()) {
        item {
          MessageText(text = stateValues.stringListEmpty)
        }
      } else {
        items(shownDebtors) { debtor ->
          DebtorPaymentCard(
            debtor = debtor,
            onClick = {
              coroutineScope.launch {
                NavigationScreenModel.Menu.CloseDebt.setState("selected_debtor_id" to debtor.id)
                Navigation.Menu.go(NavigationScreenModel.Menu.CloseDebt, forceSecond = true)
              }
            },
            onDelete = {
              pendingDeleteId = debtor.id
            }
          )

          Spacer(modifier = Modifier.height(stateValues.marginTextField))
        }
      }

      item {
        Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
      }
    }

    pendingDeleteId?.let { debtorId ->
      Dialog(
        onDismissRequest = { pendingDeleteId = null }
      ) {
        Column(
          modifier = Modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
              stateValues.unfocusedBorderWidth,
              stateValues.PlaceholderTextColor,
              RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup)
        ) {
          Text(
            text = "Delete debtor?",
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
          )

          Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actionButton(
              modifier = Modifier.weight(1f),
              text = stateValues.stringCancel,
              enabledColor = stateValues.DisabledColor
            ) {
              pendingDeleteId = null
            }

            actionButton(
              modifier = Modifier.weight(1f),
              text = stateValues.stringDelete,
              enabledColor = stateValues.ErrorColor
            ) {
              storeId?.let {
                deleteDebtor(it, debtorId)
              }
              pendingDeleteId = null
            }
          }
        }
      }
    }
  }
}

@Composable
fun AppConfiguration.MenuCloseDebtScreen() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val state by NavigationScreenModel.Menu.CloseDebt.state.collectAsState()
    val selectedId = state["selected_debtor_id"]
    val debtors by debtorsState.payload.collectAsState()
    val debtor = debtors.orEmpty().find { it.id == selectedId }
    val storeId = stateValues.activeStoreId

    ScreenAppBarWidget(
      title = stateValues.stringCloseDebt,
      iconPath = stateValues.drawablePathIconDebtors,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    if (storeId == null || debtor == null) {
      MessageText(
        modifier = Modifier.fillMaxSize(),
        text = stateValues.stringListEmpty
      )
      return@Column
    }

    var amountText by rememberSaveable(debtor.id) {
      mutableStateOf(moneyInputFromDouble(debtor.debtAmount))
    }

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
        .padding(stateValues.marginTextField)
    ) {
      item {
        DebtorPaymentCard(
          debtor = debtor,
          selected = true,
          onClick = {}
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        TransactionPaymentAmountField(
          title = stateValues.stringCloseDebt,
          value = amountText,
          selected = true,
          onSelected = {

          },
          onValueChange = { amountText = it },
          leadingIconPath = stateValues.drawablePathIconDebtors
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        TransactionNumpad(
          onInput = { token ->
            amountText = paymentInputAppend(amountText, token)
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        actionButton(
          text = "Pay full debt",
          onClick = {
            amountText = moneyInputFromDouble(debtor.debtAmount)
          }
        )

        Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
      }
    }

    actionButton(
      modifier = Modifier
        .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
        .padding(8.dp),
      text = stateValues.stringConfirm,
      enabled = amountText.toMoneyDouble() > 0.0 && stateValues.latestNotification == null,
      onClick = {
        payDebtorDebt(
          DebtPaymentRequestDataModel(
            debtorId = debtor.id,
            storeId = storeId,
            amount = amountText.toMoneyDouble().coerceAtMost(debtor.debtAmount),
            currency = debtor.currency,
            timeMillis = getCurrentTimeMillis()
          )
        ) { state ->
          if (state is DataState.Success) {
            coroutineScope.launch {
              Navigation.Menu.pop(stateValues.isNarrowScreen)
            }
          }
        }
      }
    )
  }
}


@Composable
fun AppConfiguration.MenuAppThemeScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAppTheme,
      iconPath = stateValues.drawablePathIconAppTheme,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(
          if (stateValues.isNarrowScreen) 1f else 0.6f
        )
    ) {
      items(stateValues.globalAppConfiguration.themes) { theme ->
        AppThemeSettingsItemWidget(
          id = theme.id,
          name = theme.name.extractLocalizedString(stateValues.appLanguage) ?: theme.id.toString(),
          isActive = stateValues.appThemeId == theme.id
        )
      }
    }
  }
}

@Composable
fun AppConfiguration.MenuAppModeScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ){
    ScreenAppBarWidget(
      title = stateValues.stringAppMode,
      iconPath = stateValues.drawablePathIconSwitch,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    @Composable
    fun Card(
      text: String,
      onClick: (String) -> Unit
    ) {
      Column(
        modifier = Modifier
          .clip(RoundedCornerShape(stateValues.cornerRadius))
          .border(stateValues.unfocusedBorderWidth, color = stateValues.TextColor, RoundedCornerShape(stateValues.cornerRadius))
          .clickable(
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = stateValues.TextColor)
          ) {
            onClick(text)
          }
      ) {
        Text(
          modifier = Modifier
            .padding(16.dp),
          text = text,
          color = stateValues.TextColor,
          fontWeight = FontWeight.Bold
        )
      }
    }

    if (AppConfiguration.stateValues.isNarrowScreen) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.6f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Column {
          Card(
            "Store"
          ) {
            setAppMode(0)
          }

          Spacer(modifier = Modifier.height(4.dp))

          Card(
            "Buyer"
          ) {
            setAppMode(1)
          }

          Spacer(modifier = Modifier.height(4.dp))

          Card(
            "Supplier"
          ) {
            setAppMode(2)
          }

          Spacer(modifier = Modifier.height(4.dp))

          Card(
            "Producer"
          ) {
            setAppMode(3)
          }

          Spacer(modifier = Modifier.height(4.dp))
        }
      }
    } else {
      Row(
        modifier = Modifier
          .fillMaxWidth(0.6f)
          .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Card(
          "Store"
        ) {
          setAppMode(0)
        }

        Spacer(modifier = Modifier.width(4.dp))

        Card(
          "Buyer"
        ) {
          setAppMode(1)
        }

        Spacer(modifier = Modifier.width(4.dp))

        Card(
          "Supplier"
        ) {
          setAppMode(2)
        }

        Spacer(modifier = Modifier.width(4.dp))

        Card(
          "Producer"
        ) {
          setAppMode(3)
        }

        Spacer(modifier = Modifier.width(4.dp))
      }
    }
  }
}

@Composable
fun AppConfiguration.MenuAppLanguageScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAppLanguage,
      iconPath = stateValues.drawablePathIconAppLanguage,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(
          if (stateValues.isNarrowScreen) 1f else 0.6f
        )
    ) {
      item {
        val settingsIconRes by stateValues.drawableResIconSettings.collectAsState()

        AppLanguageSettingsItemWidget(
          "system",
          flagDrawablePath = stateValues.drawablePathIconSettings,
          flagDrawableRes = settingsIconRes,
          name = stateValues.stringSystemLanguage,
          isActive = stateValues.appLanguage == "system"
        )
      }

      items(stateValues.globalAppConfiguration.languages) { language ->
        AppLanguageSettingsItemWidget(
          language = language.language,
          name = language.name.extractLocalizedString(stateValues.appLanguage) ?: language.language,
          flagDrawablePath = language.flagDrawablePath,
          flagDrawableRes = language.mapIconRes(),
          isActive = stateValues.appLanguage == language.language
        )
      }
    }
  }
}

private enum class MenuAnalyticsTab(val id: String) {
  Sales("sales"),
  Returns("returns"),
  Acceptance("acceptance"),
  Stock("stock"),
  Suppliers("suppliers"),
  Workers("workers"),
  CashRegister("cash_register");

  companion object {
    fun fromId(id: String): MenuAnalyticsTab {
      return entries.find { it.id == id } ?: Sales
    }
  }
}

private enum class AnalyticsPeriodPreset(val id: String) {
  Today("today"),
  Week("week"),
  Month("month"),
  All("all");

  companion object {
    fun fromId(id: String): AnalyticsPeriodPreset {
      return entries.find { it.id == id } ?: Month
    }
  }
}

private data class AnalyticsPeriod(
  val startMillis: Long,
  val endMillisExclusive: Long
)

private data class AnalyticsHistoryRow(
  val title: String,
  val count: Int,
  val total: Double
)

private data class AnalyticsSummaryCardData(
  val title: String,
  val value: String,
  val subtitle: String? = null
)

@Composable
fun AppConfiguration.MenuAnalyticsScreen() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAnalytics,
      iconPath = stateValues.drawablePathIconAnalytics,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    val transactionsPayload by transactionsState.payload.collectAsState()
    val transactions = transactionsPayload.orEmpty()

    val cashRegisterExtractionsPayload by cashRegisterExtractionsState.payload.collectAsState()
    val cashRegisterExtractions = cashRegisterExtractionsPayload.orEmpty()

    val currentCashRegisterAmount by cashRegisterAmountState.collectAsState()

    var selectedPeriodId by rememberSaveable {
      mutableStateOf(AnalyticsPeriodPreset.Month.id)
    }

    val selectedPeriodPreset = AnalyticsPeriodPreset.fromId(selectedPeriodId)

    val period = remember(selectedPeriodPreset) {
      selectedPeriodPreset.toAnalyticsPeriod()
    }

    val scopedTransactions = remember(
      transactions,
      stateValues.activeStoreId,
      period
    ) {
      transactions
        .filter {
          stateValues.activeStoreId == null || it.storeId == stateValues.activeStoreId
        }
        .filter {
          it.timeMillis >= period.startMillis &&
              it.timeMillis < period.endMillisExclusive
        }
    }

    val scopedCashRegisterExtractions = remember(
      cashRegisterExtractions,
      period
    ) {
      cashRegisterExtractions.filter {
        it.timeMillis >= period.startMillis &&
            it.timeMillis < period.endMillisExclusive
      }
    }

    val selectedTabContent = tabRowWidget(
      modifier = Modifier
        .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
        .padding(stateValues.marginTextField),
      tabs = listOf(
        TabContent(MenuAnalyticsTab.Sales.id, stateValues.stringSale),
        TabContent(MenuAnalyticsTab.Returns.id, stateValues.stringReturn),
        TabContent(MenuAnalyticsTab.Acceptance.id, stateValues.stringSupply),
        TabContent(MenuAnalyticsTab.Stock.id, stateValues.stringStock),
        TabContent(MenuAnalyticsTab.Suppliers.id, stateValues.stringSuppliers),
        TabContent(MenuAnalyticsTab.Workers.id, stateValues.stringWorkers),
        TabContent(MenuAnalyticsTab.CashRegister.id, "Cash register")
      )
    )

    AnalyticsPeriodSelector(
      selectedPeriodId = selectedPeriodId,
      onSelected = {
        selectedPeriodId = it
      }
    )

    val selectedTab = MenuAnalyticsTab.fromId(selectedTabContent.id)
    val currencyCode = currentAnalyticsCurrencyCode()

    when (selectedTab) {
      MenuAnalyticsTab.Sales -> {
        MenuAnalyticsTransactionScreen(
          title = stateValues.stringSale,
          transactionType = "purchase",
          transactions = scopedTransactions,
          currencyCode = currencyCode,
          emptyText = "No sales in this period"
        )
      }

      MenuAnalyticsTab.Returns -> {
        MenuAnalyticsTransactionScreen(
          title = stateValues.stringReturn,
          transactionType = "return",
          transactions = scopedTransactions,
          currencyCode = currencyCode,
          emptyText = "No returns in this period"
        )
      }

      MenuAnalyticsTab.Acceptance -> {
        MenuAnalyticsTransactionScreen(
          title = stateValues.stringSupply,
          transactionType = "accept",
          transactions = scopedTransactions,
          currencyCode = currencyCode,
          emptyText = "No supply transactions in this period"
        )
      }

      MenuAnalyticsTab.Stock -> {
        MenuAnalyticsStockScreen()
      }

      MenuAnalyticsTab.Suppliers -> {
        MenuAnalyticsSimpleScreen(
          title = stateValues.stringSuppliers,
          cards = listOf(
            AnalyticsSummaryCardData(
              title = "Connected module",
              value = "Supplier analytics",
              subtitle = "Use accepted goods grouped by supplier here"
            ),
            AnalyticsSummaryCardData(
              title = "Useful metric",
              value = "Acceptance total",
              subtitle = "Old Android version reused supplier screen from analytics"
            )
          )
        )
      }

      MenuAnalyticsTab.Workers -> {
        MenuAnalyticsSimpleScreen(
          title = stateValues.stringWorkers,
          cards = listOf(
            AnalyticsSummaryCardData(
              title = "Connected module",
              value = "Worker analytics",
              subtitle = "Use workshifts, sales per worker, and salary here"
            ),
            AnalyticsSummaryCardData(
              title = "Useful metric",
              value = "Revenue / worker",
              subtitle = "Good for cashier performance later"
            )
          )
        )
      }

      MenuAnalyticsTab.CashRegister -> {
        MenuAnalyticsCashRegisterScreen(
          currentAmount = currentCashRegisterAmount,
          extractions = scopedCashRegisterExtractions,
          currencyCode = currencyCode
        )
      }
    }
  }
}

@Composable
private fun AppConfiguration.MenuAnalyticsTransactionScreen(
  title: String,
  transactionType: String,
  transactions: List<TransactionDataModel>,
  currencyCode: String,
  emptyText: String
) {
  val typedTransactions = remember(transactions, transactionType) {
    transactions.filter { it.type == transactionType }
  }

  val totalCash = remember(typedTransactions) {
    typedTransactions.sumOf { it.paidCash }
  }

  val totalCard = remember(typedTransactions) {
    typedTransactions.sumOf { it.paidCard }
  }

  val total = totalCash + totalCard

  val average = if (typedTransactions.isNotEmpty()) {
    total / typedTransactions.size
  } else {
    0.0
  }

  val totalGoodsQuantity = remember(typedTransactions) {
    typedTransactions
      .flatMap { it.goodsInTransaction }
      .sumOf { it.quantity }
  }

  val historyRows = remember(typedTransactions) {
    typedTransactions.toMonthlyAnalyticsHistoryRows()
  }

  LazyColumn(
    modifier = Modifier
//      .weight(1f) // h1
      .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
      .padding(stateValues.marginTextField)
  ) {
    item {
      Text(
        text = title,
        color = stateValues.TextColor,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = stateValues.marginTextField)
      )
    }

    item {
      AnalyticsCardsGrid(
        cards = listOf(
          AnalyticsSummaryCardData(
            title = "Total",
            value = total.money(currencyCode),
            subtitle = "Cash + cashless"
          ),
          AnalyticsSummaryCardData(
            title = stateValues.stringCash,
            value = totalCash.money(currencyCode)
          ),
          AnalyticsSummaryCardData(
            title = stateValues.stringCashless,
            value = totalCard.money(currencyCode)
          ),
          AnalyticsSummaryCardData(
            title = "Transactions",
            value = typedTransactions.size.toString()
          ),
          AnalyticsSummaryCardData(
            title = "Average transaction",
            value = average.money(currencyCode)
          ),
          AnalyticsSummaryCardData(
            title = stateValues.stringItems,
            value = totalGoodsQuantity.cleanNumber()
          )
        )
      )
    }

    item {
      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      Text(
        text = "History",
        color = stateValues.TextColor,
        fontSize = stateValues.accentTextSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = stateValues.marginTextField)
      )
    }

    if (typedTransactions.isEmpty()) {
      item {
        MessageText(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = stateValues.marginTextFieldGroup),
          emptyText
        )
      }
    } else {
      items(historyRows) { row ->
        AnalyticsHistoryRowWidget(
          row = row,
          currencyCode = currencyCode
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))
      }
    }

    item {
      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
private fun AppConfiguration.MenuAnalyticsStockScreen() {
  val stock = stateValues.stock.orEmpty()
  val batches = stateValues.stockBatches.orEmpty()

  val activeItems = stock.count { it.isActive }
  val inactiveItems = stock.size - activeItems
  val quickItems = stock.count { it.isQuickItem }

  val activeBatches = batches.count { it.isActive }
  val inactiveBatches = batches.size - activeBatches

  LazyColumn(
    modifier = Modifier
//      .weight(1f) h1
      .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
      .padding(stateValues.marginTextField)
  ) {
    item {
      Text(
        text = stateValues.stringStock,
        color = stateValues.TextColor,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = stateValues.marginTextField)
      )
    }

    item {
      AnalyticsCardsGrid(
        cards = listOf(
          AnalyticsSummaryCardData(
            title = stateValues.stringItems,
            value = stock.size.toString(),
            subtitle = "All stock items"
          ),
          AnalyticsSummaryCardData(
            title = "Active items",
            value = activeItems.toString()
          ),
          AnalyticsSummaryCardData(
            title = "Inactive items",
            value = inactiveItems.toString()
          ),
          AnalyticsSummaryCardData(
            title = stateValues.stringQuick,
            value = quickItems.toString(),
            subtitle = "Quick-sale items"
          ),
          AnalyticsSummaryCardData(
            title = stateValues.stringBatches,
            value = batches.size.toString()
          ),
          AnalyticsSummaryCardData(
            title = "Active batches",
            value = activeBatches.toString()
          ),
          AnalyticsSummaryCardData(
            title = "Inactive batches",
            value = inactiveBatches.toString()
          )
        )
      )
    }

    item {
      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
private fun AppConfiguration.MenuAnalyticsCashRegisterScreen(
  currentAmount: Double,
  extractions: List<CashRegisterExtractionEntryDataModel>,
  currencyCode: String
) {
  val extractedTotal = remember(extractions) {
    extractions.sumOf { it.amount }
  }

  val historyRows = remember(extractions) {
    extractions
      .groupBy { it.timeMillis.monthLabel() }
      .map { (month, entries) ->
        AnalyticsHistoryRow(
          title = month,
          count = entries.size,
          total = entries.sumOf { it.amount }
        )
      }
  }

  LazyColumn(
    modifier = Modifier
//      .weight(1f) n1
      .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
      .padding(stateValues.marginTextField)
  ) {
    item {
      Text(
        text = "Cash register",
        color = stateValues.TextColor,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = stateValues.marginTextField)
      )
    }

    item {
      AnalyticsCardsGrid(
        cards = listOf(
          AnalyticsSummaryCardData(
            title = "Current amount",
            value = currentAmount.money(currencyCode)
          ),
          AnalyticsSummaryCardData(
            title = "Extracted",
            value = extractedTotal.money(currencyCode)
          ),
          AnalyticsSummaryCardData(
            title = "Extractions",
            value = extractions.size.toString()
          )
        )
      )
    }

    item {
      Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

      Text(
        text = "History",
        color = stateValues.TextColor,
        fontSize = stateValues.accentTextSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = stateValues.marginTextField)
      )
    }

    if (extractions.isEmpty()) {
      item {
        MessageText(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = stateValues.marginTextFieldGroup),
          "No cash register extractions in this period"
        )
      }
    } else {
      items(historyRows) { row ->
        AnalyticsHistoryRowWidget(
          row = row,
          currencyCode = currencyCode
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))
      }
    }

    item {
      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
private fun AppConfiguration.MenuAnalyticsSimpleScreen(
  title: String,
  cards: List<AnalyticsSummaryCardData>
) {
  LazyColumn(
    modifier = Modifier
//      .weight(1f) h1
      .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
      .padding(stateValues.marginTextField)
  ) {
    item {
      Text(
        text = title,
        color = stateValues.TextColor,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = stateValues.marginTextField)
      )
    }

    item {
      AnalyticsCardsGrid(cards)
    }

    item {
      Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
    }
  }
}

@Composable
private fun AppConfiguration.AnalyticsPeriodSelector(
  selectedPeriodId: String,
  onSelected: (String) -> Unit
) {
  val presets = listOf(
    AnalyticsPeriodPreset.Today to "Today",
    AnalyticsPeriodPreset.Week to "7 days",
    AnalyticsPeriodPreset.Month to "30 days",
    AnalyticsPeriodPreset.All to "All"
  )

  Row(
    modifier = Modifier
      .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
      .padding(horizontal = stateValues.marginTextField),
    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
  ) {
    presets.forEach { (preset, title) ->
      AnalyticsPill(
        modifier = Modifier.weight(1f),
        text = title,
        selected = selectedPeriodId == preset.id
      ) {
        onSelected(preset.id)
      }
    }
  }

  Spacer(modifier = Modifier.height(stateValues.marginTextField))
}

@Composable
private fun AppConfiguration.AnalyticsPill(
  modifier: Modifier = Modifier,
  text: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        width = stateValues.unfocusedBorderWidth,
        color = if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        shape = RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(
        if (selected) stateValues.AccentColor else stateValues.BackgroundColor
      )
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(
          color = if (selected) stateValues.AccentTextColor else stateValues.TextColor
        ),
        onClick = onClick
      )
      .padding(vertical = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
      fontSize = stateValues.smallTextSize,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun AppConfiguration.AnalyticsCardsGrid(
  cards: List<AnalyticsSummaryCardData>
) {
  if (stateValues.isNarrowScreen) {
    Column(
      verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
      cards.forEach { card ->
        AnalyticsSummaryCard(
          modifier = Modifier.fillMaxWidth(),
          card = card
        )
      }
    }
  } else {
    Column(
      verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
      cards.chunked(3).forEach { rowCards ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
          rowCards.forEach { card ->
            AnalyticsSummaryCard(
              modifier = Modifier.weight(1f),
              card = card
            )
          }

          repeat(3 - rowCards.size) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun AppConfiguration.AnalyticsSummaryCard(
  modifier: Modifier = Modifier,
  card: AnalyticsSummaryCardData
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        width = stateValues.unfocusedBorderWidth,
        color = stateValues.PlaceholderTextColor,
        shape = RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(stateValues.BackgroundColor)
      .padding(stateValues.marginTextFieldGroup)
  ) {
    Text(
      text = card.title,
      color = stateValues.PlaceholderTextColor,
      fontSize = stateValues.smallTextSize,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = card.value,
      color = stateValues.TextColor,
      fontSize = stateValues.accentTextSize,
      fontWeight = FontWeight.Bold
    )

    card.subtitle?.takeIf { it.isNotBlank() }?.let {
      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = it,
        color = stateValues.PlaceholderTextColor,
        fontSize = stateValues.smallTextSize
      )
    }
  }
}

@Composable
private fun AppConfiguration.AnalyticsHistoryRowWidget(
  row: AnalyticsHistoryRow,
  currencyCode: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        width = stateValues.unfocusedBorderWidth,
        color = stateValues.AccentColor,
        shape = RoundedCornerShape(stateValues.cornerRadius)
      )
      .background(stateValues.BackgroundColor)
      .padding(stateValues.marginTextFieldGroup),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = row.title,
        color = stateValues.TextColor,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold
      )

      Text(
        text = "${row.count} transactions",
        color = stateValues.PlaceholderTextColor,
        fontSize = stateValues.smallTextSize
      )
    }

    Text(
      text = row.total.money(currencyCode),
      color = stateValues.TextColor,
      fontSize = stateValues.accentTextSize,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.End
    )
  }
}

private fun AnalyticsPeriodPreset.toAnalyticsPeriod(): AnalyticsPeriod {
  val now = kotlin.time.Clock.System.now()
  val nowMillis = now.toEpochMilliseconds()

  return when (this) {
    AnalyticsPeriodPreset.Today -> {
      val start = todayStartMillis()
      AnalyticsPeriod(
        startMillis = start,
        endMillisExclusive = nowMillis + 1
      )
    }

    AnalyticsPeriodPreset.Week -> {
      AnalyticsPeriod(
        startMillis = nowMillis - 7L * 24L * 60L * 60L * 1000L,
        endMillisExclusive = nowMillis + 1
      )
    }

    AnalyticsPeriodPreset.Month -> {
      AnalyticsPeriod(
        startMillis = nowMillis - 30L * 24L * 60L * 60L * 1000L,
        endMillisExclusive = nowMillis + 1
      )
    }

    AnalyticsPeriodPreset.All -> {
      AnalyticsPeriod(
        startMillis = 0L,
        endMillisExclusive = Long.MAX_VALUE
      )
    }
  }
}
private fun todayStartMillis(): Long {
  val timeZone = TimeZone.currentSystemDefault()
  val today = kotlin.time.Clock.System.now()
    .toLocalDateTime(timeZone)
    .date

  return today
    .atStartOfDayIn(timeZone)
    .toEpochMilliseconds()
}

private fun List<TransactionDataModel>.toMonthlyAnalyticsHistoryRows(): List<AnalyticsHistoryRow> {
  return groupBy { it.timeMillis.monthLabel() }
    .map { (month, transactions) ->
      AnalyticsHistoryRow(
        title = month,
        count = transactions.size,
        total = transactions.sumOf { it.paidCash + it.paidCard }
      )
    }
}

private fun Long.monthLabel(): String {
  val date = Instant
    .fromEpochMilliseconds(this)
    .toLocalDateTime(TimeZone.currentSystemDefault())

  val month = date.monthNumber.toString().padStart(2, '0')
  val year = date.year.toString()

  return "$month.$year"
}

private fun Double.money(currencyCode: String): String {
  return if (currencyCode.isBlank()) {
    fixed2()
  } else {
    "${fixed2()} $currencyCode"
  }
}

private fun Double.fixed2(): String {
  val negative = this < 0
  val scaled = round(abs(this) * 100.0).toLong()

  val whole = scaled / 100
  val cents = (scaled % 100).toString().padStart(2, '0')

  return "${if (negative) "-" else ""}$whole.$cents"
}

private fun Double.cleanNumber(): String {
  return if (this % 1.0 == 0.0) {
    toLong().toString()
  } else {
    fixed2()
  }
}

private fun AppConfiguration.currentAnalyticsCurrencyCode(): String {
  val countryLocale = stateValues.userAccount?.countryLocale

  return stateValues.globalAppConfiguration
    .countries
    .find { it.locale.equals(countryLocale, ignoreCase = true) }
    ?.currencies
    ?.firstOrNull()
    ?.code
    ?: ""
}

//@Composable
//fun AppConfiguration.MenuAnalyticsScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    ScreenAppBarWidget(
//      title = stateValues.stringAnalytics,
//      iconPath = stateValues.drawablePathIconAnalytics,
//      onBack = {
//        coroutineScope.launch {
//          Navigation.Menu.pop(stateValues.isNarrowScreen)
//        }
//      }
//    )
//  }
//}

@Composable
fun AppConfiguration.MenuAddEditWorkerScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAddWorker,
      iconPath = stateValues.drawablePathIconAdd,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxHeight()
        .fillMaxWidth(0.5f)
        .padding(vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      item {
        val outerSpace = 16.dp
        val innerSpace = 8.dp

        val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val emailTextFieldContent = emailTextField(
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.KEY_STATE_EMAIL
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val firstNameTextFieldContent = genericTextField(
          stateHost = NavigationScreenModel.Menu.AddEditWorker,
          stateKey = NavigationScreenModel.KEY_STATE_FIRST_NAME,
          titleText = stateValues.stringFirstName,
          placeholderText = stateValues.stringEnterFirstName,
          leadingIconPath = stateValues.drawablePathIconPerson,
          contentInvalidText = stateValues.stringFirstNameCannotBeEmptyOrJustWhitespaces,
          onContentValidityCheck = {
            it.checkAsPersonName()
          },
          onFilterValue = {
            it.filterAsPersonName()
          }
        )

        Spacer(modifier = Modifier.height(innerSpace))

        val lastNameTextFieldContent = genericTextField(
          stateHost = NavigationScreenModel.Menu.AddEditWorker,
          stateKey = NavigationScreenModel.KEY_STATE_LAST_NAME,
          titleText = stateValues.stringLastName,
          placeholderText = stateValues.stringEnterLastName,
          leadingIconPath = stateValues.drawablePathIconPerson,
          contentInvalidText = stateValues.stringLastNameCannotBeEmptyOrJustWhitespaces,
          onContentValidityCheck = {
            it.checkAsPersonName()
          },
          onFilterValue = {
            it.filterAsPersonName()
          }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        var goAction: (() -> Unit)? = null

        val confirmationPasswordTextFieldContent: GenericTextFieldContent? = passwordTextField(
          titleText = stateValues.stringConfirmationPassword,
          placeholderText = stateValues.stringRequiredToEditAccount,
          contentInvalidText = stateValues.stringRequiredToEditAccount + ". \n" + stateValues.stringPasswordMustBe,
          imeWithAction = ImeWithAction(ImeAction.Go) {
            goAction?.invoke()
          },
          stateHost = NavigationScreenModel.Menu.UserAccount,
          stateKey = NavigationScreenModel.Menu.UserAccount.KEY_STATE_CONFIRMATION_PASSWORD
        )

        Spacer(modifier = Modifier.height(outerSpace))

        Text(
          text = "If everything is correct the user will be invited", // TODO
          fontSize = stateValues.textSize,
          color = stateValues.TextColor,
          modifier = Modifier
            .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(outerSpace))

        goAction = {
          softKeyboardController?.hide()

          phoneNumberTextFieldContent.checkContentValidity()
          emailTextFieldContent.checkContentValidity()

          firstNameTextFieldContent.checkContentValidity()
          lastNameTextFieldContent.checkContentValidity()

          confirmationPasswordTextFieldContent?.checkContentValidity()

          if (
            phoneNumberTextFieldContent.isContentValid
            && emailTextFieldContent.isContentValid
            && firstNameTextFieldContent.isContentValid
            && lastNameTextFieldContent.isContentValid
            && confirmationPasswordTextFieldContent!!.isContentValid
          ) {
            addStoreWorker(
              phoneNumber = stateValues.globalAppConfiguration.countries.run {
                find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
              }.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase(),
              email = emailTextFieldContent.value.text.trim().lowercase(),
              firstName = firstNameTextFieldContent.value.text.trim(),
              lastName = lastNameTextFieldContent.value.text.trim(),
              password = confirmationPasswordTextFieldContent?.value?.text!!
            )

            confirmationPasswordTextFieldContent.reset()
          }
        }

        actionButton(
          text = stateValues.stringAddWorker,
          enabled = stateValues.latestNotification == null
        ) {
          goAction.invoke()
        }

        Spacer(
          modifier = Modifier
            .height(stateValues.screenHeight / 10)
        )
      }
    }
  }
}

@Composable
fun MenuAddEditSupplierScreen() {
}

@Composable
fun AppConfiguration.MenuAddEditStoreScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val editedStore = NavigationScreenModel.Menu.AddEditStore.state.value[NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID]?.run { stateValues.stores?.find { store -> store.id == this } }

    ScreenAppBarWidget(
      title = if (editedStore != null) stateValues.stringEditStore else stateValues.stringAddStore,
      iconPath = stateValues.drawablePathIconAdd,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
          if (editedStore != null)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID)
        }
      }
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth(
          if (stateValues.isNarrowScreen) 1f else 0.6f
        )
        .weight(1f)
        .padding(start = 8.dp, top = 24.dp, end = 8.dp)
    ) {
      item {
        val outerSpace = 16.dp

        val nameData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringName,
          placeholderText = stateValues.stringEnterName,
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.KEY_STATE_NAME,
          domains = emptyList(),
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            add(
              SelectableDomain(
                id = "main",
                displayId = stateValues.stringMain,
                name = stateValues.stringMain,
                iconPath = null,
                iconRes = null
              )
            )

            stateValues.globalAppConfiguration.languages.forEach { language ->
              add(
                SelectableDomain(
                  id = language.language,
                  displayId = language.name,
                  name = language.name,
                  iconPath = language.flagDrawablePath,
                  iconRes = language.mapIconRes()
                )
              )
            }
          },
          secondaryDomainsShowName = false,
          addDomainActionButtonText = "",
          addSecondaryDomainActionButtonText = stateValues.stringAddName,
        )

        LaunchedEffect(nameData.data) {
          println("name data is ${nameData.data}")
        }
        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        val aliasData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringAlias,
          placeholderText = stateValues.stringEnterAlias,
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.Menu.AddEditStore.KEY_STATE_ALIAS,
          domains = emptyList(),
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            add(
              SelectableDomain(
                id = "main",
                displayId = stateValues.stringMain,
                name = stateValues.stringMain,
                iconPath = null,
                iconRes = null,
              )
            )

            stateValues.globalAppConfiguration.languages.forEach { language ->
              add(
                SelectableDomain(
                  id = language.language,
                  displayId = language.name,
                  name = language.name,
                  iconPath = language.flagDrawablePath,
                  iconRes = language.mapIconRes(),
                )
              )
            }
          },
          secondaryDomainsShowName = false,
          addDomainActionButtonText = "",
          addSecondaryDomainActionButtonText = stateValues.stringAddTranslation,
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        val descriptionData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringDescription,
          placeholderText = stateValues.stringEnterDescription,
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.Menu.AddEditStore.KEY_STATE_DESCRIPTION,
          domains = emptyList(),
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            add(
              SelectableDomain(
                id = "main",
                displayId = stateValues.stringMain,
                name = stateValues.stringMain,
                iconPath = null,
                iconRes = null,
              )
            )

            stateValues.globalAppConfiguration.languages.forEach { language ->
              add(
                SelectableDomain(
                  id = language.language,
                  displayId = language.name,
                  name = language.name,
                  iconPath = language.flagDrawablePath,
                  iconRes = language.mapIconRes()
                )
              )
            }
          },
          secondaryDomainsShowName = false,
          addDomainActionButtonText = "",
          addSecondaryDomainActionButtonText = stateValues.stringAddTranslation
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
          valueInitial = editedStore?.phoneNumbers?.takeIf { it.isNotEmpty() }?.first(),
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        var goAction: (() -> Unit)? = null
        val emailTextFieldContent = emailTextField(
          valueInitial = editedStore?.emails?.takeIf { it.isNotEmpty() }?.first(),
          stateHost = NavigationScreenModel.Menu.AddEditStore,
          stateKey = NavigationScreenModel.KEY_STATE_EMAIL
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        val companyFormDropdownListContent = dropdownListWidget(
          titleText = stateValues.stringCompanyForm,
          domains = stateValues.globalAppConfiguration.companyForms.map {
            SelectableDomain(
              id = it.id,
              displayId = it.name,
              name = it.name,
              iconPath = null,
              iconRes = null
            )
          },
          showName = false,
          selectedInitial = editedStore?.companyForms?.takeIf { it.isNotEmpty() }?.first()?.id
        )

        Spacer(modifier = Modifier.height(outerSpace))

        goAction = {
          softKeyboardController?.hide()

//          nameTextFieldContent.checkContentValidity()

          phoneNumberTextFieldContent.checkContentValidity()
          emailTextFieldContent.checkContentValidity()

          if (
//            nameTextFieldContent.isContentValid &&
            phoneNumberTextFieldContent.isContentValid
            && emailTextFieldContent.isContentValid
          ) {
            if (editedStore != null) {
              updateStore(
                store = StoreDataModel(
                  id = editedStore.id,
                  userIds = emptyList(),
                  storeTypeIds = emptyList(),
                  name = nameData.data.map {
                    LocalizedStringDataModel(
                      it.selectedSecondaryDomainId,
                      it.value.text
                    )
                  },
                  alias = aliasData.data.map {
                    LocalizedStringDataModel(
                      it.selectedSecondaryDomainId,
                      it.value.text
                    )
                  },
                  description = descriptionData.data.map {
                    LocalizedStringDataModel(
                      it.selectedSecondaryDomainId,
                      it.value.text
                    )
                  },
                  companyForms = stateValues.globalAppConfiguration.companyForms.find { it.id == companyFormDropdownListContent.selectedId }!!.run { listOf(this) },
                  location = stateValues.globalAppConfiguration.countries.first().cities.first().run {
                    LocationDataModel(
                      name = name.extractLocalizedString(stateValues.appLanguage) ?: "Some location",
                      postalIndex = "020000",
                      latitude = centerLatitude,
                      longitude = centerLongitude
                    )
                  },
                  phoneNumbers = listOf(
                    stateValues.globalAppConfiguration.countries.run {
                      find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                    }.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase()
                  ),
                  emails = listOf(emailTextFieldContent.value.text.trim().lowercase()),
                  countryLocales = listOf(),
                  createdAt = 0L
                )
              ) {
                coroutineScope.launch {
                  Navigation.Menu.pop()
                  NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID)
                }
              }
            } else {
              addStore(
                store = StoreDataModel(
                  id = "",
                  userIds = emptyList(),
                  storeTypeIds = emptyList(),
                  name = nameData.data.map {
                    LocalizedStringDataModel(
                      it.selectedSecondaryDomainId,
                      it.value.text
                    )
                  },
                  alias = aliasData.data.map {
                    LocalizedStringDataModel(
                      it.selectedSecondaryDomainId,
                      it.value.text
                    )
                  },
                  description = descriptionData.data.map {
                    LocalizedStringDataModel(
                      it.selectedSecondaryDomainId,
                      it.value.text
                    )
                  },
                  companyForms = stateValues.globalAppConfiguration.companyForms.find { it.id == companyFormDropdownListContent.selectedId }
                  !!.run { listOf(this) },
                  location = stateValues.globalAppConfiguration.countries.first().cities.first().run {
                    LocationDataModel(
                      name = name.extractLocalizedString(stateValues.appLanguage) ?: "Some location",
                      postalIndex = "020000",
                      latitude = centerLatitude,
                      longitude = centerLongitude
                    )
                  },
                  phoneNumbers = listOf(
                    stateValues.globalAppConfiguration.countries.run {
                      find { it.locale.equals(phoneNumberTextFieldContent.selectedId, true) } ?: first()
                    }.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase()
                  ),
                  emails = listOf(emailTextFieldContent.value.text.trim().lowercase()),
                  countryLocales = emptyList(),
                  createdAt = 0L
                )
              ) {
                coroutineScope.launch {
                  Navigation.Menu.pop()
                }
              }
            }
          }
        }

        actionButton(
          text = if (editedStore != null) stateValues.stringEditStore else stateValues.stringAddStore,
          enabled = stateValues.latestNotification == null
        ) {
          goAction.invoke()
        }
      }

      item {
        Spacer(
          modifier = Modifier
            .height(stateValues.screenHeight / 4)
        )
      }
    }
  }
}

@Composable
fun MenuAddEditGoodsCategoryScreen() {
}

@Composable
fun AppConfiguration.MainScreen() {
  Column(
    modifier = Modifier.background(stateValues.BackgroundColor).windowInsetsPadding(WindowInsets.systemBars)
      .fillMaxSize()
  ) {
    val showNavigationBar = stateValues.navigationScreensMain.last().run {
      this !is NavigationScreenModel.Splash && this !is NavigationScreenModel.UserAuth
    }

    coroutineScope.launch(Dispatchers.ourIo) {
      userAccountState.value.collect {
        if (it is DataState.Empty) {
          Navigation.goMain(NavigationScreenModel.UserAuth.Main)
        } else if ((it is DataState.Success) && Navigation.Main.value.last()
            .run { this is NavigationScreenModel.UserAuth || this is NavigationScreenModel.Splash }
        ) Navigation.goMain(NavigationScreenModel.Transaction.MainSale)
      }
    }

    Box(
      modifier = Modifier.weight(1f)
    ) {
      AnimatedContent(stateValues.navigationScreensMain, label = "") {
        when (stateValues.navigationScreensMain.last()) {
          is NavigationScreenModel.Splash -> {
            SplashScreen()
          }

          is NavigationScreenModel.UserAuth -> UserAuthScreen()

          is NavigationScreenModel.Transaction.MainSale, NavigationScreenModel.Transaction.MainReturn, NavigationScreenModel.Transaction.MainSupply -> TransactionScreen()

          is NavigationScreenModel.Stock -> StockScreen()

          is NavigationScreenModel.Menu -> MenuScreen()

          else -> {}
        }
      }
    }


    if (showNavigationBar) Column(
      modifier = Modifier.clip(
        RoundedCornerShape(
          topStart = stateValues.cornerRadius, topEnd = stateValues.cornerRadius
        )
      ).border(
        stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(
          topStart = stateValues.cornerRadius, topEnd = stateValues.cornerRadius
        )
      ).fillMaxWidth().height(if (stateValues.latestNotification != null) 80.dp else 56.dp).wrapContentHeight(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      stateValues.latestNotification?.run {
        Box(
          modifier = Modifier.fillMaxWidth().height(stateValues.marginTextFieldGroup).background(
            when (type) {
              NotificationType.Neutral -> stateValues.PlaceholderTextColor
              NotificationType.Positive -> stateValues.OkayColor
              NotificationType.Negative -> stateValues.ErrorColor
            }
          ), contentAlignment = Alignment.Center
        ) {
          Text(
            text = message,
            color = stateValues.AccentTextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Row(
        modifier = Modifier.weight(1f).run {
          if (stateValues.isNarrowScreen) fillMaxWidth()
          else width((stateValues.boundWidgetWidth * 2))
        }) {
        with(stateValues.navigationScreensMain) {
          val items = when (stateValues.appModeId) {
            0 -> Navigation.bottomNavBarScreensStore
            else -> Navigation.bottomNavBarScreensBuyer
          }

          items.forEach { model ->
            val isSelected = model.route == stateValues.navigationScreensMain.last().route

            val iconTintColor by animateColorAsState(
              targetValue = if (isSelected) stateValues.AccentColor else stateValues.IconTintColor,
              label = "",
            )

            Column(
              modifier = Modifier.weight(1f).clickable(
                onClick = {
                  coroutineScope.launch {
                    Navigation.goMain(model)
                  }
                }, interactionSource = remember {
                  MutableInteractionSource()
                }, indication = ripple(color = stateValues.TextColor)
              ), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
              CpImage(
                modifier = Modifier.padding(top = 8.dp).weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true),
                url = model.iconPath,
                fallbackRes = model.iconRes,
                contentDescription = model.name,
                tintColor = iconTintColor
              )

              Text(
                text = model.name,
                color = iconTintColor,
                textAlign = TextAlign.Center,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(4.dp)
              )
            }
          }
        }
      }
    } else {
      stateValues.latestNotification?.run {
        Box(
          modifier = Modifier.fillMaxWidth().height(32.dp).background(
            when (type) {
              NotificationType.Neutral -> stateValues.PlaceholderTextColor
              NotificationType.Positive -> stateValues.OkayColor
              NotificationType.Negative -> stateValues.ErrorColor
            }
          ), contentAlignment = Alignment.Center
        ) {
          Text(
            text = message,
            color = stateValues.AccentTextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    val simpleDialogContentL by simpleDialogContent.collectAsState()

    simpleDialogContentL?.let {
      SimpleDialogWidget(
        title = it.first, positiveAction = it.second, negativeAction = it.third
      )
    }
  }
}

private val _simpleDialogContent =
  MutableStateFlow<Triple<String, Pair<String, () -> Unit>, Pair<String, () -> Unit>>?>(null)
val simpleDialogContent = _simpleDialogContent.asStateFlow()

suspend fun postSimpleDialogContent(
  title: String, positiveAction: Pair<String, () -> Unit>, negativeAction: Pair<String, () -> Unit>
) {
  _simpleDialogContent.emit(Triple(title, positiveAction, negativeAction))
}

suspend fun resetSimpleDialogContent() {
  _simpleDialogContent.emit(null)
}

@Composable
fun AppConfiguration.LargeIconWithTitleWidget(
  modifier: Modifier = Modifier,
  imageUrl: String,
  imageRes: DrawableResource,
  title: String = "",
  titleTextSize: TextUnit = stateValues.titleTextSize,
  titleTextColor: Color = stateValues.TextColor,
  contentDescription: String = title
) {

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {

    CpImage(
      url = imageUrl,
      fallbackRes = imageRes,
      contentDescription = contentDescription
    )

    Spacer(modifier = Modifier.height(stateValues.screenHeight / 24))

    title.takeIf { it.isNotEmpty() }?.run {
      Text(
        text = title,
        style = TextStyle(
          color = titleTextColor,
          fontSize = titleTextSize,
          fontWeight = FontWeight.Bold
        )
      )
    }
  }
}

val kamelConfig = KamelConfig {
  httpUrlFetcher {
    httpCache(cacheSize)

    defaultRequest {
      headers.append(HttpHeaders.CacheControl, "no-cache")
      headers.append(HttpHeaders.Pragma, "no-cache") // legacy proxies
    }

    install(HttpRequestRetry) {
      maxRetries = 2
      retryIf { _, response -> !response.status.isSuccess() }
    }
  }
  fileFetcher()
  takeFrom(KamelConfig.Core)

  svgDecoder()
  imageBitmapDecoder()
}

@Composable
fun CpImage(
  modifier: Modifier = Modifier,
  url: String,
  fallbackRes: DrawableResource?,
  contentDescription: String?,
  tintColor: Color? = null
) {
  var failed by rememberSaveable {
    mutableStateOf(false)
  }

  val cf = tintColor?.let { ColorFilter.tint(it) }

  CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
    if (failed) {
      if (fallbackRes != null)
        Image(
          modifier = modifier,
          painter = painterResource(fallbackRes),
          contentDescription = contentDescription,
          contentScale = ContentScale.FillWidth,
          colorFilter = cf,
        )
    } else {
      KamelImage(
        modifier = modifier,
        resource = {
          asyncPainterResource(
            data = Url(getFullDrawableRemoteResourceUrl(url))
          )
        },
        contentScale = ContentScale.FillWidth,
        contentDescription = contentDescription,
        colorFilter = cf,
        onFailure = {
          failed = true
        }
      )
    }
  }
}

class ImeWithAction(
  val ime: ImeAction,
  private val action: (() -> Unit)? = null
) {

  companion object {

    val Default: ImeWithAction = ImeWithAction(ImeAction.Companion.Next)
  }

  fun getKeyboardActions(): KeyboardActions {
    return action?.run {
      when (ime.toString()) {
        "Go" -> KeyboardActions(
          onGo = {
            action()
          }
        )
        "Search" -> KeyboardActions(
          onSearch = {
            action()
          }
        )
        "Send" -> KeyboardActions(
          onSend = {
            action()
          }
        )
        "Previous" -> KeyboardActions(
          onPrevious = {
            action()
          }
        )
        "Next" -> KeyboardActions(
          onNext = {
            action()
          }
        )
        "Done" -> KeyboardActions(
          onDone = {
            action()
          }
        )
        else -> KeyboardActions.Companion.Default
      }
    } ?: KeyboardActions.Companion.Default
  }
}

@Composable
fun AppConfiguration.GoodsItemInStockWidget(
  modifier: Modifier = Modifier,
  index: Int? = null,
  goodsItem: GoodsItemDataModel,
  batches: List<GoodsBatchDataModel> = emptyList(),
  textColor: Color = stateValues.TextColor,
  soldForPeriod: QuantityDataModel? = null,
  returnedForPeriod: QuantityDataModel? = null,
  onClick: ((GoodsItemDataModel) -> Unit)? = null,
  onDelete: ((GoodsItemDataModel) -> Unit)? = null,
  onEdit: ((GoodsItemDataModel) -> Unit)? = null
) {
  val itemName = goodsItem.name.visibleLocalizedString(stateValues.appLanguage, "Unnamed item")

  val barcodesText = goodsItem.barcodes
    .filter { it.isNotBlank() }
    .joinToString(", ")

  val categoriesText = goodsItem.categoryIds
    .mapNotNull { categoryId ->
      stateValues.goodsCategories
        .orEmpty()
        .find { it.id == categoryId }
        ?.name
        ?.visibleLocalizedString(stateValues.appLanguage, categoryId)
    }
    .joinToString(", ")

  val totalQuantity = batches.sumOf { it.quantity.total }
  val quantityUnitText = batches
    .firstOrNull()
    ?.quantity
    ?.immutableUnitName
    ?.extractLocalizedString(stateValues.appLanguage)
    .orEmpty()

  val activeBatch = batches.find { it.id == goodsItem.activeShelfBatchId }
    ?: batches.bestBatchForSale(goodsItem)

  val expirationStatusText = activeBatch?.expirationDateMillis?.let {
    "Exp: $it"
  }

  Row(
    modifier
      .padding(bottom = 4.dp)
      .fillMaxHeight()
      .run {
        onClick?.run {
          clickable(
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = textColor),
            onClick = {
              this(goodsItem)
            }
          )
        } ?: this
      }
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        stateValues.PlaceholderTextColor,
        RoundedCornerShape(
          stateValues.cornerRadius
        )
      )
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          modifier = Modifier.weight(1f),
          text = index?.run { "${index + 1}.  $itemName" } ?: itemName,
          fontSize = stateValues.titleTextSize,
          fontWeight = FontWeight.Bold,
          color = textColor,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        if (goodsItem.isQuickItem) {
          Spacer(modifier = Modifier.width(8.dp))

          Text(
            text = stateValues.stringQuick,
            color = stateValues.AccentColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      if (barcodesText.isNotBlank()) {
        StockCardInfoLine(
          title = stateValues.stringBarcode,
          value = barcodesText,
          textColor = textColor
        )
      }

      if (categoriesText.isNotBlank()) {
        StockCardInfoLine(
          title = stateValues.stringCategory,
          value = categoriesText,
          textColor = textColor
        )
      }

      StockCardInfoLine(
        title = "Stock",
        value = if (batches.isEmpty()) {
          "No batches"
        } else {
          "$totalQuantity $quantityUnitText • ${batches.size} batch${if (batches.size == 1) "" else "es"}"
        },
        textColor = if (batches.isEmpty()) stateValues.ErrorColor else textColor
      )

      activeBatch?.let {
        StockCardInfoLine(
          title = "Shelf",
          value = listOfNotNull(
            if (it.id == goodsItem.activeShelfBatchId) "active" else "auto",
            it.shelfPosition,
            expirationStatusText
          ).joinToString(" • "),
          textColor = stateValues.AccentColor
        )
      }

      if (goodsItem.salePrices.isNotEmpty()) {
        StockCardInfoLine(
          title = stateValues.stringSale,
          value = goodsItem.salePrices.joinToString(" / ") { "${it.price} ${it.currency}" },
          textColor = textColor
        )
      }

      if (goodsItem.supplyPrices.isNotEmpty()) {
        StockCardInfoLine(
          title = stateValues.stringSupply,
          value = goodsItem.supplyPrices.joinToString(" / ") { "${it.price} ${it.currency}" },
          textColor = textColor
        )
      }

      goodsItem.note?.takeIf { it.isNotBlank() }?.let {
        StockCardInfoLine(
          title = "Note",
          value = it,
          textColor = stateValues.PlaceholderTextColor
        )
      }
    }

    Column(
      modifier = Modifier
        .padding(end = 12.dp, top = 12.dp, start = 4.dp, bottom = 12.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      onEdit?.let {
        actionButton(
          text = "",
          iconPath = stateValues.drawablePathIconEdit,
          iconContentDescription = stateValues.drawablePathIconEdit,
        ) {
          onEdit(goodsItem)
        }
      }

      onDelete?.let {
        actionButton(
          text = "",
          enabledColor = stateValues.ErrorColor,
          iconPath = stateValues.drawablePathIconDelete,
          iconContentDescription = stateValues.drawablePathIconDelete,
        ) {
          onDelete(goodsItem)
        }
      }
    }
  }
}


@Composable
private fun AppConfiguration.StockCardInfoLine(
  title: String,
  value: String,
  textColor: Color
) {
  if (value.isBlank()) return

  Text(
    text = "$title: $value",
    fontSize = stateValues.textSize,
    color = textColor,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis
  )
}

@Composable
private fun AppConfiguration.StockScreenScaffold(
  title: String,
  iconPath: String? = stateValues.drawablePathIconStock,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = title,
      iconPath = iconPath,
      onBack = if (!Navigation.Stock.isVeryFirstScreen(stateValues.isNarrowScreen)) {
        {
          coroutineScope.launch {
            Navigation.Stock.pop(stateValues.isNarrowScreen)
          }
        }
      } else null
    )

    content()
  }
}

@Composable
private fun AppConfiguration.rememberSelectedStockItem(
  stateHost: StateHost,
  stateKey: String
): GoodsItemDataModel? {
  val screenState by stateHost.state.collectAsState()
  val goodsItemId = screenState[stateKey]

  return stateValues.stock
    .orEmpty()
    .find { it.id == goodsItemId }
}

@Composable
fun AppConfiguration.StockGoodsItemDetailsScreen() {
  val goodsItem = rememberSelectedStockItem(
    stateHost = NavigationScreenModel.Stock.GoodsItemDetails,
    stateKey = NavigationScreenModel.Stock.GoodsItemDetails.KEY_STATE_GOODS_ITEM_ID
  )

  StockScreenScaffold(
    title = "Goods item"
  ) {
    if (goodsItem == null) {
      MessageText(
        modifier = Modifier.weight(1f),
        text = "Goods item was not found"
      )
      return@StockScreenScaffold
    }

    val batches = stateValues.stockBatches.orEmpty().filter { it.goodsItemId == goodsItem.id && it.isActive }
    val itemName = goodsItem.name.extractLocalizedString(stateValues.appLanguage)
      ?: goodsItem.name.firstOrNull()?.value
      ?: "Unnamed item"

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(stateValues.marginTextField)
    ) {
      item {
        Text(
          text = itemName,
          color = stateValues.TextColor,
          fontSize = stateValues.titleTextSize,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        StockCardInfoLine(stateValues.stringBarcode, goodsItem.barcodes.joinToString(", "), stateValues.TextColor)
        StockCardInfoLine(
          stateValues.stringCategory,
          goodsItem.categoryIds.mapNotNull { goodsCategoryName(it) }.joinToString(", "),
          stateValues.TextColor
        )
        StockCardInfoLine("Measurement unit", goodsItem.measurementUnitId, stateValues.TextColor)
        StockCardInfoLine("Batches", batches.size.toString(), stateValues.TextColor)
        StockCardInfoLine(stateValues.stringSale, goodsItem.salePrices.joinToString(" / ") { "${it.price} ${it.currency}" }, stateValues.TextColor)
        StockCardInfoLine(stateValues.stringReturn, goodsItem.returnPrices.joinToString(" / ") { "${it.price} ${it.currency}" }, stateValues.TextColor)
        StockCardInfoLine(stateValues.stringSupply, goodsItem.supplyPrices.joinToString(" / ") { "${it.price} ${it.currency}" }, stateValues.TextColor)

        goodsItem.description.takeIf { it.isNotEmpty() }?.let {
          Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
          Text("Description", color = stateValues.TextColor, fontWeight = FontWeight.Bold)
          it.forEach { line ->
            Text(
              text = "${line.language}: ${line.value}",
              color = stateValues.TextColor,
              fontSize = stateValues.textSize
            )
          }
        }

        goodsItem.note?.takeIf { it.isNotBlank() }?.let {
          Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
          Text("Note", color = stateValues.TextColor, fontWeight = FontWeight.Bold)
          Text(it, color = stateValues.TextColor, fontSize = stateValues.textSize)
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      actionButton(
        modifier = Modifier.weight(1f),
        text = "Batches"
      ) {
        coroutineScope.launch {
          NavigationScreenModel.Stock.GoodsItemBatches.setState(
            NavigationScreenModel.Stock.GoodsItemBatches.KEY_STATE_GOODS_ITEM_ID to goodsItem.id
          )
          Navigation.Stock.go(NavigationScreenModel.Stock.GoodsItemBatches, remove = true, forceSecond = true)
        }
      }

      actionButton(
        modifier = Modifier.weight(1f),
        text = "Supplier prices"
      ) {
        coroutineScope.launch {
          NavigationScreenModel.Stock.GoodsItemSupplierPrices.setState(
            NavigationScreenModel.Stock.GoodsItemSupplierPrices.KEY_STATE_GOODS_ITEM_ID to goodsItem.id
          )
          Navigation.Stock.go(NavigationScreenModel.Stock.GoodsItemSupplierPrices, remove = true, forceSecond = true)
        }
      }
    }
  }
}

@Composable
fun AppConfiguration.StockGoodsItemBatchesScreen() {
  val goodsItem = rememberSelectedStockItem(
    stateHost = NavigationScreenModel.Stock.GoodsItemBatches,
    stateKey = NavigationScreenModel.Stock.GoodsItemBatches.KEY_STATE_GOODS_ITEM_ID
  )

  StockScreenScaffold(
    title = "Batches"
  ) {
    StockAddEditBatchesPage(
      modifier = Modifier.weight(1f),
      goodsItem = goodsItem
    )
  }
}

@Composable
fun AppConfiguration.StockGoodsItemSupplierPricesScreen() {
  val goodsItem = rememberSelectedStockItem(
    stateHost = NavigationScreenModel.Stock.GoodsItemSupplierPrices,
    stateKey = NavigationScreenModel.Stock.GoodsItemSupplierPrices.KEY_STATE_GOODS_ITEM_ID
  )

  StockScreenScaffold(
    title = "Supplier prices"
  ) {
    StockSupplierPricesPage(
      modifier = Modifier.weight(1f),
      goodsItem = goodsItem
    )
  }
}

@Composable
fun AppConfiguration.StockGoodsItemOrdersScreen() {
  val goodsItem = rememberSelectedStockItem(
    stateHost = NavigationScreenModel.Stock.GoodsItemOrders,
    stateKey = NavigationScreenModel.Stock.GoodsItemOrders.KEY_STATE_GOODS_ITEM_ID
  )

  StockScreenScaffold(
    title = "Supplier orders"
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxSize()
        .padding(stateValues.marginTextField),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      MessageText(
        text = if (goodsItem == null) {
          "Goods item was not found"
        } else {
          "Supplier orders screen is ready as a navigation destination. The order creation/receiving form is the next safe layer to connect."
        }
      )
    }
  }
}


@Composable
fun AppConfiguration.genericTextField(
  modifier: Modifier = Modifier,

  titleText: String = "",

  stateHost: StateHost? = null,
  stateKey: String? = null,

  enabled: Boolean = true,
  readOnly: Boolean = false,
  wide: Boolean = false,
  valueInitial: String? = null,

  textSize: TextUnit = stateValues.textSize,
  textColor: Color = stateValues.TextColor,

  titleTextSize: TextUnit = stateValues.accentTextSize,
  titleTextColor: Color = textColor,

  isFocusedInitial: Boolean = false,
  updateIsFocusedAction: ((FocusState) -> Unit)? = null,
  forceRefocus: Boolean = false,
  placeholderText: String = "",
  placeholderTextSize: TextUnit = stateValues.textSize,
  placeholderTextColor: Color = stateValues.PlaceholderTextColor,

  selectionFocusTextColor: Color = stateValues.AccentTextColor,
  selectionBackgroundColor: Color = stateValues.AccentColor,

  focusedBorderWidth: Dp = stateValues.focusedBorderWidth,
  unfocusedBorderWidth: Dp = stateValues.unfocusedBorderWidth,

  focusedBorderColor: Color = stateValues.AccentColor,
  unfocusedBorderColor: Color = textColor,

  backgroundColor: Color = stateValues.BackgroundColor,

  cornerRadius: Dp = stateValues.cornerRadius,
  cornerShape: Shape = RoundedCornerShape(cornerRadius),
  keyboardType: KeyboardType = KeyboardType.Text,
  imeWithAction: ImeWithAction? = null,

  leadingIconPath: String? = null,
  leadingIcon: @Composable (() -> Unit)? = null,
  leadingIconContentDescription: String = placeholderText,
  trailingIcon: @Composable (() -> Unit)? = null,
  trailingIconExtraPath: String? = null,
  trailingIconExtraContentDescription: String = placeholderText,
  trailingIconExtraOnClick: (() -> Unit)? = null,
  visualTransformation: (TextFieldValue) -> TransformedText = {
    getTransformedTextWithSelectionFocusTextColor(it, selectionFocusTextColor)
  },
  showClearButton: Boolean = true,
  contentInvalidText: String? = null,
  onContentValidityCheck: ((String) -> Boolean)? = null,
  onFilterValue: ((String) -> Boolean)? = null,
  onValueChange: ((String, () -> Unit) -> Unit)? = null
): GenericTextFieldContent {

  val state: Map<String, String>? by stateHost?.state?.collectAsState() ?: remember {
    mutableStateOf(emptyMap())
  }

  val stateValue = state?.get(stateKey)

  var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
    val initial = stateValue ?: valueInitial ?: ""
    mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
  }

  var isFocused by rememberSaveable {
    mutableStateOf(isFocusedInitial)
  }

  var focusRequester by remember {
    mutableStateOf(FocusRequester())
  }

  var isContentValid by rememberSaveable {
    mutableStateOf(true)
  }

  LaunchedEffect(valueInitial) {
    if (valueInitial != null) {
      textFieldValue = TextFieldValue(valueInitial, selection = TextRange(valueInitial.length))
    }
  }

  Column(
    modifier = modifier
  ) {
    val titleTextPresent = titleText.isNotEmpty() && titleText.isNotBlank()

    if (titleTextPresent)
      Text(
        modifier = Modifier
          .padding(bottom = 4.dp),
        text = titleText,
        style = TextStyle(
          color = titleTextColor,
          fontSize = titleTextSize,
          fontWeight = FontWeight.Bold
        )
      )

    val textSelectionColors = TextSelectionColors(
      handleColor = selectionBackgroundColor,
      backgroundColor = selectionBackgroundColor
    )

    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
      BasicTextField(
        value = textFieldValue,
        onValueChange = {
          if (onValueChange != null) {
            onValueChange(it.text) {
              if (onFilterValue == null || onFilterValue(it.text)) {
                textFieldValue = it

                stateKey?.run {
                  coroutineScope.launch {
                    stateHost?.setState(stateKey to it.text)
                  }
                }

              }
            }
          } else {
            if (onFilterValue == null || onFilterValue(it.text)) {
              textFieldValue = it
              stateKey?.run {
                coroutineScope.launch {
                  stateHost?.setState(stateKey to it.text)
                }
              }
            }
          }
        },
        enabled = enabled,
        readOnly = readOnly,
        modifier = Modifier
          .height(if (wide) stateValues.wideTextFieldHeight else stateValues.textFieldHeight)
          .clip(cornerShape)
          .background(backgroundColor)
          .border(
            width = if (isFocused) focusedBorderWidth else unfocusedBorderWidth,
            color = if (isFocused) focusedBorderColor else unfocusedBorderColor,
            shape = cornerShape
          )
          .focusRequester(focusRequester)
          .onFocusChanged {
            isFocused = it.isFocused

            updateIsFocusedAction?.invoke(it)
          },
        keyboardOptions = KeyboardOptions.Default.copy(
          keyboardType = keyboardType,
          imeAction = (imeWithAction ?: ImeWithAction(ime = ImeAction.Default)).ime
        ),
        keyboardActions = (imeWithAction ?: ImeWithAction(ime = ImeAction.Default)).getKeyboardActions(),
        textStyle = TextStyle(
          fontSize = textSize,
          color = textColor
        ),
        visualTransformation = {
          visualTransformation(textFieldValue)
        },
        singleLine = true,
        cursorBrush = SolidColor(selectionBackgroundColor),
        decorationBox = { innerTextField ->
          Box(
            modifier = modifier
              .fillMaxWidth()
          ) {
            Row(
              Modifier
                .fillMaxSize(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              leadingIcon?.invoke() ?: leadingIconPath?.run {
                CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
                  CpImage(
                    modifier = Modifier
                      .padding(
                        start = 12.dp,
                        top = stateValues.textFieldIconPadding,
                        bottom = stateValues.textFieldIconPadding
                      )
                      .size(stateValues.iconSize),
                    url = leadingIconPath,
                    fallbackRes = Res.drawable._9_0,
                    contentDescription = leadingIconContentDescription
                  )
                }
              }

              Box(
                Modifier
                  .weight(1f)
                  .run {
//                    if (wide)
//                      fillMaxHeight().padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
//                    else
                    padding(start = 12.dp)
                  }
              ) {
                Text(
                  text = if (textFieldValue.text.isEmpty()) placeholderText else "",
                  fontSize = placeholderTextSize,
                  color = placeholderTextColor,
                  textAlign = TextAlign.Start,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )

                innerTextField()
              }

              CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
                Row(
                  modifier = Modifier
                    .fillMaxHeight(),
                ) {
                  trailingIcon?.invoke() ?: trailingIconExtraPath?.run {
                    Box(
                      modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                          interactionSource = remember {
                            MutableInteractionSource()
                          },
                          indication = ripple(color = textColor, radius = cornerRadius),
                          onClick = {
                            trailingIconExtraOnClick?.invoke()
                            isFocused = true
                          }
                        )
                    ) {
                      CpImage(
                        modifier = Modifier
                          .padding(horizontal = 10.dp, vertical = stateValues.textFieldIconPadding)
                          .size(stateValues.iconSize),
                        url = trailingIconExtraPath,
                        fallbackRes = Res.drawable._9_0,
                        contentDescription = trailingIconExtraContentDescription
                      )
                    }
                  }

                  if (showClearButton && textFieldValue.text.isNotEmpty()) {
                    Box(
                      modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                          interactionSource = remember {
                            MutableInteractionSource()
                          },
                          indication = ripple(color = textColor, radius = cornerRadius)
                        ) {
                          val applyClear = {
                            textFieldValue = TextFieldValue("")

                            stateKey?.run {
                              coroutineScope.launch {
                                stateHost?.setState(stateKey to "")
                              }
                            }
                          }

                          if (onValueChange != null) {
                            onValueChange("") {
                              applyClear()
                            }
                          } else {
                            applyClear()
                          }

                          isFocused = true
                          focusRequester.requestFocus()
                        }
                    ) {
                      CpImage(
                        modifier = Modifier
                          .padding(
                            start = 4.dp,
                            top = stateValues.textFieldIconPadding,
                            bottom = stateValues.textFieldIconPadding,
                            end = 12.dp
                          )
                          .size(stateValues.iconSize),
                        url = stateValues.drawablePathIconCancel,
                        fallbackRes = Res.drawable._9_0,
                        contentDescription = stateValues.stringClear
                      )
                    }
                  }
                }
              }
            }
          }
        }
      )
    }

    if (!isContentValid && contentInvalidText != null && contentInvalidText.isNotEmpty() && contentInvalidText.isNotBlank()) {
      Text(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 2.dp),
        text = contentInvalidText,
        style = TextStyle(
          color = stateValues.ErrorColor,
          fontSize = stateValues.smallTextSize,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
      )
    }

    LaunchedEffect(isFocusedInitial) {
      if (isFocusedInitial) {
        delay(300)

        isFocused = true
        focusRequester.requestFocus()
      }
    }

    LaunchedEffect(forceRefocus, isFocused) {
      if (forceRefocus && !isFocused) {
        isFocused = true
        focusRequester.requestFocus()
      }
    }
  }

  val content = GenericTextFieldContent(
    value = textFieldValue,
    isFocused = isFocused,
    focusRequester = focusRequester,
    isContentValid = isContentValid,
    onContentValidityCheck = onContentValidityCheck?.run {
      {
        val value = this(textFieldValue.text)
        isContentValid = value
        value
      }
    },
    onReset = {
      textFieldValue = TextFieldValue()
    }
  )

  onContentValidityCheck?.let {
    LaunchedEffect(textFieldValue, isContentValid) {
      if (!isContentValid) {
        isContentValid = onContentValidityCheck(textFieldValue.text)
      }
    }
  }

  return content
}

class GenericTextFieldContent(
  var value: TextFieldValue,
  var isFocused: Boolean,
  var focusRequester: FocusRequester,
  var isContentValid: Boolean,
  val onContentValidityCheck: ((String) -> Boolean)? = null,
  val onReset: (() -> Unit)? = null
) {

  fun checkContentValidity() {
    isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
  }

  fun reset() {
    onReset?.invoke()
  }
}

fun String.toColor(): Color {
  return Color(toULong(radix = 16).toInt())
}

fun AppLanguageDataModel.mapIconRes(): DrawableResource {
  return when (language) {
    "en" -> Res.drawable.flag_en
    "ru" -> Res.drawable.flag_ru
    "tj" -> Res.drawable.flag_tj
    else -> Res.drawable.flag_kz
  }
}

fun CountryDataModel.mapIconRes(): DrawableResource {
  return when (locale) {
    "en" -> Res.drawable.flag_en
    "ru" -> Res.drawable.flag_ru
    "tj" -> Res.drawable.flag_tj
    else -> Res.drawable.flag_kz
  }
}

@Composable
fun AppConfiguration.responseText(
  invalidText: String,
  color: Color = stateValues.ErrorColor,
  showIf: () -> Boolean
): ResponseTextContent {
  var show by rememberSaveable {
    mutableStateOf(false)
  }

  show = showIf()

  if (show) {
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp),
      text = invalidText,
      style = TextStyle(
        color = color,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    )
  }

  return ResponseTextContent(
    isActual = !show,
    onActualityCheck = {
      val value = showIf()
      show = value
      value
    }
  )
}

class ResponseTextContent(
  var isActual: Boolean,
  private val onActualityCheck: () -> Boolean
) {

  fun checkContentValidity() {
    isActual = onActualityCheck.invoke()
  }
}

@Composable
fun AppConfiguration.emailTextField(
  modifier: Modifier = Modifier,
  stateHost: StateHost,
  stateKey: String,
  valueInitial: String? = null,
  imeWithAction: ImeWithAction? = null,
): GenericTextFieldContent {

  return genericTextField(
    modifier = modifier,
    valueInitial = valueInitial,
    titleText = stateValues.stringEmail,
    stateHost = stateHost,
    stateKey = stateKey,
    placeholderText = stateValues.stringEnterEmailAddress,
    leadingIconPath = stateValues.drawablePathIconEmail,
    keyboardType = KeyboardType.Email,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    contentInvalidText = stateValues.stringEmailMustBe,
    onContentValidityCheck = {
      it.checkAsEmail()
    }
  )
}

@Composable
fun AppConfiguration.dropdownListWidget(
  modifier: Modifier = Modifier,
  titleText: String,
  textColor: Color = stateValues.TextColor,
  titleTextSize: TextUnit = stateValues.accentTextSize,
  titleTextColor: Color = textColor,
  showId: Boolean = true,
  showName: Boolean = false,
  domains: List<SelectableDomain>,
  selectedInitial: String? = null,
  cornerRadius: Dp = stateValues.cornerRadius,
  search: Triple<String?, StateHost?, String?>? = null
): DropdownListWidgetContent {

  var selectedId by rememberSaveable {
    mutableStateOf(selectedInitial ?: domains.first().id)
  }

  var selected by remember {
    mutableStateOf(domains.find { it.id.equals(selectedId, true) } ?: domains.first())
  }

  LaunchedEffect(selectedId) {
    selected = domains.find { it.id.equals(selectedId, true) } ?: domains.first()
  }

  LaunchedEffect(selectedInitial) {
    selectedInitial?.let {
      selectedId = it
    }
  }

  val isDomainSelectionDropdownExpandedState = remember {
    MutableTransitionState(false)
      .apply {
        targetState = false
      }
  }

  Column(
    modifier = modifier
  ) {
    val titleTextPresent = titleText.isNotEmpty() && titleText.isNotBlank()

    if (titleTextPresent)
      Text(
        modifier = Modifier
          .padding(bottom = 4.dp),
        text = titleText,
        style = TextStyle(
          color = titleTextColor,
          fontSize = titleTextSize,
          fontWeight = FontWeight.Bold
        )
      )

    selectableDomainWidget(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(cornerRadius))
        .height(stateValues.textFieldHeight)
        .border(
          width = if (isDomainSelectionDropdownExpandedState.targetState) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
          color = if (isDomainSelectionDropdownExpandedState.targetState) stateValues.AccentColor else textColor,
          shape = RoundedCornerShape(cornerRadius)
        ),
      textColor = textColor,
      domain = selected,
      showId = showId,
      showName = showName,
      showExpansion = true
    ) {
      isDomainSelectionDropdownExpandedState.targetState =
        !isDomainSelectionDropdownExpandedState.targetState
    }

    if (domains.isNotEmpty()) {
      Spacer(modifier = Modifier.height(1.dp))

      AnimatedVisibility(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 0.dp, max = stateValues.screenHeight / 3),
        visibleState = isDomainSelectionDropdownExpandedState,
        enter = expandVertically(),
        exit = shrinkVertically()
      ) {
        Column(
          modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .fillMaxWidth()
            .height((domains.size * stateValues.textFieldHeight.value).dp)
            .border(
              width = stateValues.focusedBorderWidth,
              color = stateValues.AccentColor,
              shape = RoundedCornerShape(cornerRadius)
            )
        ) {
          val searchTextFieldContent: GenericTextFieldContent? = if (search != null) {
            Spacer(modifier = Modifier.height(1.dp))

            searchTextField(
              modifier = Modifier
                .fillMaxWidth(),
              stateHost = search.second,
              stateKey = search.third,
              focusedBorderWidth = 0.dp,
              unfocusedBorderWidth = 0.dp,
              focusedBorderColor = Color.Transparent,
              unfocusedBorderColor = Color.Transparent
            )
          } else null

          Spacer(modifier = Modifier.height(1.dp))

          LazyColumn(

          ) {
            itemsIndexed(
              domains.run {
                val query = searchTextFieldContent?.value?.text

                if (query?.isNotEmpty() == true)
                  filter { it.searchContains(query) }
                else this
              }
            ) { index, domain ->
              selectableDomainWidget(
                modifier = Modifier
                  .fillParentMaxWidth(),
                textColor = textColor,
                domain = domain,
                showId = showId,
                showName = showName
              ) {
                selectedId = domains[index].id

                isDomainSelectionDropdownExpandedState.targetState =
                  !isDomainSelectionDropdownExpandedState.targetState
              }
            }
          }
        }
      }
    }

  }

  return DropdownListWidgetContent(selectedId = selectedId)
}

class DropdownListWidgetContent(var selectedId: String)

@Composable
fun AppConfiguration.domainSelectionTextFieldGroupWidget(
  modifier: Modifier = Modifier,
  titleText: String,
  placeholderText: String,
  stateHost: StateHost? = null,
  stateKey: String? = null,
  valueInitial: List<DomainSelectionTextFieldGroupItemContent>? = null,
  domains: List<SelectableDomain>,
  secondaryDomains: List<SelectableDomain>? = null,
  secondaryDomainsShowId: Boolean = true,
  secondaryDomainsShowName: Boolean = true,
  addDomainActionButtonText: String,
  addSecondaryDomainActionButtonText: String? = null,
  keyboardType: KeyboardType = KeyboardType.Text,
  isFocusedInitial: Boolean = false,
  onFilterValue: ((String, String, String?) -> Boolean)? = null,
  onContentValidityCheck: ((String, String, String?) -> Boolean)? = null,
): DomainSelectionTextFieldGroupWidgetContent {
  var isContentValid by rememberSaveable {
    mutableStateOf(true)
  }

  var data: List<DomainSelectionTextFieldGroupItemContent> by rememberSaveable {
    mutableStateOf(
      valueInitial?.takeIf { it.isNotEmpty() } ?: listOf(
        TextFieldValue("").run {
          DomainSelectionTextFieldGroupItemContent(
            this,
            domains.takeIf {
              it.isNotEmpty()
            }?.first()?.id ?: "", secondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
            isContentValid = isContentValid,
            onContentValidityCheck = onContentValidityCheck?.let {
              {
                val value = it(this@run.text, domains.takeIf { d ->
                  d.isNotEmpty()
                }?.first()?.id ?: "", secondaryDomains?.takeIf { sd ->
                  sd.isNotEmpty()
                }?.first()?.id ?: "")
                isContentValid = value
                value
              }
            }
          )
        }

      )
    )
  }

  LaunchedEffect(valueInitial) {
    valueInitial?.run {
      data = this
    }
  }

  var availableDomains by rememberSaveable { mutableStateOf(domains) }
  var availableSecondaryDomains by rememberSaveable { mutableStateOf(secondaryDomains) }

  LaunchedEffect(data) {
    availableDomains = domains.filter { domain -> data.find { it.selectedDomainId == domain.id } == null }
    availableSecondaryDomains =
      secondaryDomains?.filter { secondaryDomain -> data.find { it.selectedSecondaryDomainId == secondaryDomain.id } == null }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
  ) {
    data.forEachIndexed { index, item ->
      val instance = domainSelectionTextField(
        titleText = if (data.size == 1 && index == 0) titleText else "$titleText ${index + 1}",
        placeholderText = placeholderText,
        valueInitial = item.value.text,
        titleIconButtonPath = if (data.size == 1) null else stateValues.drawablePathIconDelete,
        onTitleIconButtonClick = if (data.size == 1) null else {
          {
            data = data.toMutableList().apply {
              removeAt(index)

              if (index != data.lastIndex) {
                coroutineScope.launch {
                  for (i in (index + 1)..data.lastIndex) {
                    stateHost?.state?.value["${stateKey}_$i"]?.run {
                      stateHost.setState("${stateKey}_${i - 1}" to this)
                    }
                  }
                }
              }
            }
          }
        },
        domains = (listOfNotNull(domains.find { it.id == item.selectedDomainId }) + availableDomains)
          .distinctBy { it.id },
        selectedInitial = item.selectedDomainId,
        secondaryDomains = secondaryDomains?.let { allSecondaryDomains ->
          (listOfNotNull(allSecondaryDomains.find { it.id == item.selectedSecondaryDomainId }) + availableSecondaryDomains.orEmpty())
            .distinctBy { it.id }
        },
        selectedSecondaryInitial = item.selectedSecondaryDomainId,
        displayFullDomain = true,
        secondaryDomainsShowId = secondaryDomainsShowId,
        secondaryDomainsShowName = secondaryDomainsShowName,
        keyboardType = keyboardType,
        isFocusedInitial = isFocusedInitial && index == data.lastIndex,
        onFilterValue = onFilterValue,
        onContentValidityCheck = onContentValidityCheck
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextField))

      LaunchedEffect(instance.value.text) {
        if (data[index].value.text != instance.value.text) {
          data = data.toMutableList().apply {
            set(index, get(index).copy(value = instance.value))
          }
        }
      }
      LaunchedEffect(instance.selectedId) {
        try {
          data = data.toMutableList().apply {
            set(index, get(index).copy(selectedDomainId = instance.selectedId))
          }
        } catch (thr: Throwable) {

        }
      }
      LaunchedEffect(instance.selectedSecondaryId) {
        instance.selectedSecondaryId?.let {
          try {
            data = data.toMutableList().apply {
              set(index, get(index).copy(selectedSecondaryDomainId = it))
            }
          } catch (thr: Throwable) {

          }
        }
      }
    }

    if (availableDomains.isNotEmpty())
      actionButton(
        modifier = Modifier
          .fillMaxWidth(),
        text = addDomainActionButtonText,
        iconPath = stateValues.drawablePathIconAdd
      ) {
        data = data.toMutableList().apply {
          add(
            DomainSelectionTextFieldGroupItemContent(
              TextFieldValue(),
              availableDomains.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
              availableSecondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
              isContentValid
            )
          )
        }
      }

    if (addSecondaryDomainActionButtonText != null && !addSecondaryDomainActionButtonText.equals(
        addDomainActionButtonText,
        true
      ) && availableSecondaryDomains?.isNotEmpty() == true
    )
      actionButton(
        modifier = Modifier
          .fillMaxWidth(),
        text = addSecondaryDomainActionButtonText,
        iconPath = stateValues.drawablePathIconAdd
      ) {
        data = data.toMutableList().apply {
          add(
            DomainSelectionTextFieldGroupItemContent(
              TextFieldValue(),
              availableDomains.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
              availableSecondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
              isContentValid
            )
          )
        }
      }
  }

  return DomainSelectionTextFieldGroupWidgetContent(data)
}

data class DomainSelectionTextFieldGroupWidgetContent(
  val data: List<DomainSelectionTextFieldGroupItemContent>
)

data class DomainSelectionTextFieldGroupItemContent(
  var value: TextFieldValue,
  var selectedDomainId: String,
  var selectedSecondaryDomainId: String,
  var isContentValid: Boolean,
  val onContentValidityCheck: ((String) -> Boolean)? = null
) {

  fun checkContentValidity() {
    isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
  }
}

@Composable
fun AppConfiguration.domainSelectionTextField(
  modifier: Modifier = Modifier,
  valueInitial: String? = null,
  titleText: String = "",
  stateHost: StateHost? = null,
  stateKey: String? = null,
  placeholderText: String = "",
  titleIconButtonPath: String? = null,
  titleIconButtonRes: DrawableResource? = null,
  onTitleIconButtonClick: (() -> Unit)? = null,
  lockedDomainId: String? = null,
  lockedSecondaryDomainId: String? = null,
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
    mutableStateOf(lockedDomainId ?: selectedInitial)
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

  LaunchedEffect(selectedInitial, lockedDomainId) {
    selectedId = lockedDomainId ?: selectedInitial.takeIf { it.isNotEmpty() } ?: try {
      domains.first().id
    } catch (thr: Throwable) {
      ""
    }
  }

  var selectedSecondaryId by rememberSaveable {
    mutableStateOf(lockedSecondaryDomainId ?: selectedSecondaryInitial)
  }

  var selectedSecondary by remember {
    mutableStateOf(secondaryDomains?.find { it.id.equals(selectedSecondaryId, true) } ?: secondaryDomains?.first())
  }

  LaunchedEffect(selectedSecondaryId) {
    secondaryDomains?.find { it.id.equals(selectedSecondaryId, true) }?.run {
      selectedSecondary = this
    }
  }

  LaunchedEffect(selectedSecondaryInitial, lockedSecondaryDomainId) {
    selectedSecondaryId = lockedSecondaryDomainId ?: selectedSecondaryInitial
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
        if (titleIconButtonPath != null && titleIconButtonRes != null)
          CpImage(
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
            url = titleIconButtonPath,
            fallbackRes = titleIconButtonRes,
            contentDescription = titleText
          )

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
      if (selectionSecondaryEnabled && !secondaryDomains.isNullOrEmpty() && lockedSecondaryDomainId == null) {
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
              .takeIf { it.isNotEmpty() && searchTextFieldContent!!.value.text.isNotEmpty() }
              ?.search<SelectableDomain>(query = searchTextFieldContent!!.value.text)
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

                    if (lockedSecondaryDomainId == null) {
                      selectedSecondaryId = domain.id

                      isSecondaryDomainSelectionDropdownExpandedState.targetState =
                        !isSecondaryDomainSelectionDropdownExpandedState.targetState
                    }
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
              showExpansion = lockedSecondaryDomainId == null,
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
                    if (lockedDomainId == null) {
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

object AppConfiguration {

  interface StateValues {
    val latestNotification: NotificationDataModel?
    val userAccountState: DataState<UserAccountDataModel>
    val userAccount: UserAccountDataModel?

    //    val activeModeId: String?
    val stockState: DataState<List<GoodsItemDataModel>>
    val stock: List<GoodsItemDataModel>?
    val stockBatches: List<GoodsBatchDataModel>?

    val storesState: DataState<List<StoreDataModel>>
    val stores: List<StoreDataModel>?
    val activeStoreId: String?
    val goodsCategoriesState: DataState<List<GenericGoodsCategoryDataModel>>
    val goodsCategories: List<GenericGoodsCategoryDataModel>?

    val suppliersState: DataState<List<SupplierDataModel>>
    val suppliers: List<SupplierDataModel>?

    val navigationScreensMain: List<NavigationScreenModel>
    val navigationTransactionSaleClientId: Int
    val navigationTransactionReturnClientId: Int
    val navigationTransactionSupplyClientId: Int
    val navigationScreensTransactionSaleLeftClient1: List<NavigationScreenModel>
    val navigationScreensTransactionSaleLeftClient2: List<NavigationScreenModel>
    val navigationScreensTransactionSaleLeftClient3: List<NavigationScreenModel>
    val navigationScreensTransactionSaleLeftClient4: List<NavigationScreenModel>
    val navigationScreensTransactionSaleLeftClient5: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient1: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient2: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient3: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient4: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient5: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient1: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient2: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient3: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient4: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient5: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient1: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient2: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient3: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient4: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient5: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient1: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient2: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient3: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient4: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient5: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient1: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient2: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient3: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient4: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient5: List<NavigationScreenModel>
    val navigationScreensStockLeft: List<NavigationScreenModel>
    val navigationScreensStockRight: List<NavigationScreenModel>
    val navigationScreensMenuLeft: List<NavigationScreenModel>
    val navigationScreensMenuRight: List<NavigationScreenModel>

    val navigationScreensUserAuthLeft: List<NavigationScreenModel>
    val navigationScreensUserAuthRight: List<NavigationScreenModel>

    val globalAppConfiguration: GlobalAppConfigurationDataModel
    val strings: List<LocalizedStringGroupDataModel>?
    val dimensions: List<StylizedDimensionGroupDataModel>?
    val colors: List<StylizedColorGroupDataModel>?
    val drawables: List<StylizedDrawablePathsGroupDataModel>?

    val appLanguage: String
    val appThemeId: Long
    val appSizeModeId: Long
    val appModeId: Int

    val stringAppName: String
    val stringLogIn: String
    val stringPhoneNumber: String
    val stringEnterPhoneNumber: String
    val stringEmail: String
    val stringEnterEmailAddress: String
    val stringPassword: String
    val stringEnterPassword: String
    val stringCancel: String
    val stringClear: String
    val stringAuthenticationFailed: String
    val stringPhoneNumberMustBe: String
    val stringEmailMustBe: String
    val stringPasswordMustBe: String
    val stringRepeatPassword: String
    val stringPasswordsMustMatch: String
    val stringFirstName: String
    val stringLastName: String
    val stringEnterFirstName: String
    val stringEnterLastName: String
    val stringUserWithThisPhoneNumberIsAlreadyRegistered: String
    val stringUserWithThisEmailAddressIsAlreadyRegistered: String
    val stringSignUp: String
    val stringConfirm: String
    val stringSale: String
    val stringReturn: String
    val stringSupply: String
    val stringStock: String
    val stringMenu: String
    val stringBack: String
    val stringAddGoodsItem: String
    val stringEditGoodsItem: String
    val stringUserAccount: String
    val stringGoodsCategories: String
    val stringAddGoodsCategory: String
    val stringEditGoodsCategory: String
    val stringStores: String
    val stringAddStore: String
    val stringEditStore: String
    val stringSubscription: String
    val stringSubscriptionPlans: String
    val stringTransactionHistory: String
    val stringReceipt: String
    val stringAnalytics: String
    val stringWorkers: String
    val stringAddWorker: String
    val stringEditWorker: String
    val stringSuppliers: String
    val stringAddSupplier: String
    val stringEditSupplier: String
    val stringDebtors: String
    val stringCloseDebt: String
    val stringDevices: String
    val stringAppLanguage: String
    val stringAppTheme: String
    val stringSelect: String
    val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String
    val stringFirstNameCannotBeEmptyOrJustWhitespaces: String
    val stringLastNameCannotBeEmptyOrJustWhitespaces: String
    val stringSystemLanguage: String
    val stringBluetoothPermissionRequired: String
    val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String
    val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings: String
    val stringBluetoothDisabled: String
    val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String
    val stringSearchByAnyData: String
    val stringListEmpty: String
    val stringNoMatches: String
    val stringName: String
    val stringBarcode: String
    val stringSupplyPrice: String
    val stringSalePrice: String
    val stringReturnPrice: String
    val stringCategory: String
    val stringSupplier: String
    val stringEnterName: String
    val stringEnterBarcode: String
    val stringEnterSupplyPrice: String
    val stringEnterSalePrice: String
    val stringEnterReturnPrice: String
    val stringSelectCategory: String
    val stringSelectSupplier: String
    val stringEdit: String
    val stringChangePassword: String
    val stringNewPassword: String
    val stringEnterNewPassword: String
    val stringRepeatNewPassword: String
    val stringConfirmationPassword: String
    val stringRequiredToEditAccount: String
    val stringAccountSuccessfullyUpdated: String
    val stringLoggingOut: String
    val stringSessionTimeExpiredLoggingOut: String
    val stringAlias: String
    val stringDescription: String
    val stringEnterAlias: String
    val stringEnterDescription: String
    val stringOptional: String
    val stringLoggingIn: String
    val stringSigningUp: String
    val stringCompanyForm: String
    val stringMeasurementUnit: String

    val stringNoActiveStore: String
    val stringSelectInMenu: String
    val stringSupplyData: String
    val stringSaleData: String
    val stringReturnData: String
    val stringAddSupplyData: String
    val stringAddSaleData: String
    val stringAddReturnData: String

    val stringAddBarcode: String
    val stringAddName: String
    val stringPayment: String
    val stringAll: String
    val stringQuick: String
    val stringCategories: String
    val stringMain: String
    val stringAddTranslation: String
    val stringSetActive: String
    val stringOutOfStock: String
    val stringDelete: String
    val stringCash: String
    val stringCashless: String
    val stringMixed: String
    val stringAdd: String
    val stringSubtract: String
    val stringCurrentBatchData: String
    val stringEnterQuantity: String
    val stringAddQuantityData: String
    val stringShelfBatch: String
    val stringActiveStore: String
    val stringMakeInactive: String
    val stringCartEmpty: String
    val stringComplete: String
    val stringNoActiveWorkshift: String
    val stringCart: String
    val stringAppMode: String
    val stringFinances: String
    val stringItems: String
    val stringBatches: String
    val stringStandardPricesForSuppliers: String
    val stringEditableForIndividualBatches: String
    val stringBatchesData: String

    val screenWidth: Dp
    val screenHeight: Dp
    val wideScreenMinWidth: Float
    val boundWidgetWidth: Dp

    val isNarrowScreen: Boolean

    val textSize: TextUnit
    val titleTextSize: TextUnit
    val smallTextSize: TextUnit
    val accentTextSize: TextUnit

    val focusedBorderWidth: Dp
    val unfocusedBorderWidth: Dp
    val cornerRadius: Dp
    val iconSize: Dp
    val textFieldHeightMultiplierRelativeToTextSize: Float
    val textFieldHeight: Dp
    val wideTextFieldHeight: Dp

    val textFieldIconPadding: Dp

    val marginTextField: Dp
    val marginTextFieldGroup: Dp

    val AccentColor: Color
    val BackgroundColor: Color
    val TextColor: Color
    val AccentTextColor: Color
    val PlaceholderTextColor: Color
    val DisabledColor: Color
    val ErrorColor: Color

    val IconTintColor: Color
    val OkayColor: Color
    val BorderlineBadColor: Color

    val drawablePathAITALogo: String
    val drawableResAITALogo: StateFlow<DrawableResource>

    val drawablePathIconPassword: String
    val drawableResIconPassword: StateFlow<DrawableResource>

    val drawablePathIconCancel: String
    val drawableResIconCancel: StateFlow<DrawableResource>

    val drawablePathIconEyeHide: String
    val drawableResIconEyeHide: StateFlow<DrawableResource>

    val drawablePathIconEyeShow: String
    val drawableResIconEyeShow: StateFlow<DrawableResource>

    val drawablePathIconEmail: String
    val drawableResIconEmail: StateFlow<DrawableResource>
    val drawablePathIconPhone: String
    val drawableResIconPhone: StateFlow<DrawableResource>

    val drawablePathIconExpandMore: String
    val drawableResIconExpandMore: StateFlow<DrawableResource>

    val drawablePathIconExpandLess: String
    val drawableResIconExpandLess: StateFlow<DrawableResource>

    val drawablePathIconPerson: String
    val drawableResIconPerson: StateFlow<DrawableResource>

    val drawablePathIconTransactionSale: String
    val drawableResIconTransactionSale: StateFlow<DrawableResource>

    val drawablePathIconTransactionReturn: String
    val drawableResIconTransactionReturn: StateFlow<DrawableResource>

    val drawablePathIconTransactionSupply: String
    val drawableResIconTransactionSupply: StateFlow<DrawableResource>

    val drawablePathIconStock: String
    val drawableResIconStock: StateFlow<DrawableResource>

    val drawablePathIconMenu: String
    val drawableResIconMenu: StateFlow<DrawableResource>

    val drawablePathIconBackArrow: String
    val drawableResIconBackArrow: StateFlow<DrawableResource>

    val drawablePathIconAdd: String
    val drawableResIconAdd: StateFlow<DrawableResource>

    val drawablePathIconUserAccount: String
    val drawableResIconUserAccount: StateFlow<DrawableResource>

    val drawablePathIconGoodsCategories: String
    val drawableResIconGoodsCategories: StateFlow<DrawableResource>

    val drawablePathIconStores: String
    val drawableResIconStores: StateFlow<DrawableResource>

    val drawablePathIconTransactionHistory: String
    val drawableResIconTransactionHistory: StateFlow<DrawableResource>

    val drawablePathIconAnalytics: String
    val drawableResIconAnalytics: StateFlow<DrawableResource>

    val drawablePathIconWorkers: String
    val drawableResIconWorkers: StateFlow<DrawableResource>

    val drawablePathIconSuppliers: String
    val drawableResIconSuppliers: StateFlow<DrawableResource>

    val drawablePathIconDebtors: String
    val drawableResIconDebtors: StateFlow<DrawableResource>

    val drawablePathIconDevices: String
    val drawableResIconDevices: StateFlow<DrawableResource>

    val drawablePathIconAppLanguage: String
    val drawableResIconAppLanguage: StateFlow<DrawableResource>

    val drawablePathIconAppTheme: String
    val drawableResIconAppTheme: StateFlow<DrawableResource>

    val drawablePathIconCheck: String
    val drawableResIconCheck: StateFlow<DrawableResource>

    val drawablePathIconEdit: String
    val drawableResIconEdit: StateFlow<DrawableResource>

    val drawablePathIconSettings: String
    val drawableResIconSettings: StateFlow<DrawableResource>

    val drawablePathIconSearch: String
    val drawableResIconSearch: StateFlow<DrawableResource>

    val drawablePathIconBarcodeCamScanner: String
    val drawableResIconBarcodeCamScanner: StateFlow<DrawableResource>

    val drawablePathIconDelete: String
    val drawableResIconDelete: StateFlow<DrawableResource>

    val drawablePathIconExit: String
    val drawableResIconExit: StateFlow<DrawableResource>

    val drawablePathIconSwitch: String
    val drawableResIconSwitch: StateFlow<DrawableResource>

    val drawablePathIconCart: String
    val drawableResIconCart: StateFlow<DrawableResource>

    val drawablePathIconAddCart: String
    val drawableResIconAddCart: StateFlow<DrawableResource>

    val drawablePathIconSubtract: String
    val drawableResIconSubtract: StateFlow<DrawableResource>

    val drawablePathIconReceipt: String
    val drawableResIconReceipt: StateFlow<DrawableResource>

    val drawablePathIconFinances: String
    val drawableResIconFinances: StateFlow<DrawableResource>

    suspend fun updateDrawableResources()
  }

  private val _screenWidthState = MutableStateFlow(0f.dp)
  private val _screenHeightState = MutableStateFlow(0f.dp)
  private val _wideScreenMinWidthState = MutableStateFlow(600f)
  private val _boundWidgetWidthState = MutableStateFlow(280f.dp)

  private val _isNarrowScreenState = MutableStateFlow(false)

  private val _textSizeState = MutableStateFlow(14.sp)
  private val _titleTextSizeState = MutableStateFlow(20.sp)
  private val _accentTextSizeState = MutableStateFlow(16.sp)
  private val _smallTextSizeState = MutableStateFlow(12.sp)
  private val _focusedBorderWidthState = MutableStateFlow(1.dp)
  private val _unfocusedBorderWidthState = MutableStateFlow(0.5.dp)
  private val _cornerRadiusState = MutableStateFlow(14.dp)
  private val _iconSizeState = MutableStateFlow(24.dp)
  private val _textFieldHeightMultiplierRelativeToTextSizeState = MutableStateFlow(2.6f)
  private val _textFieldHeightState =
    MutableStateFlow(((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
  private val _wideTextFieldHeightState =
    MutableStateFlow((((_textSizeState.value.value * 4) * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
  private val _textFieldIconPaddingState = MutableStateFlow((9.dp))

  private val _marginTextFieldState = MutableStateFlow((8.dp))
  private val _marginTextFieldGroupState = MutableStateFlow((24.dp))

  private val _AccentColorState = MutableStateFlow(Color(0xffffba24))
  private val _BackgroundColorState = MutableStateFlow(Color(0xffffffff))
  private val _TextColorState = MutableStateFlow(Color(0x00000000))
  private val _AccentTextColorState = MutableStateFlow(Color(0xffffffff))
  private val _PlaceholderTextColorState = MutableStateFlow(Color(0xaa000000))
  private val _DisabledColorState = MutableStateFlow(Color(0xffa7a7a7))
  private val _ErrorColorState = MutableStateFlow(Color(0xffff0000))
  private val _IconTintColorState = MutableStateFlow(Color(0xffffffff))

  private val _OkayColorState = MutableStateFlow(Color(0xff6bb522))
  private val _BorderlineBadColorState = MutableStateFlow(Color(0xffffa500))

  lateinit var stateValues: StateValues

  var softKeyboardController: SoftwareKeyboardController? = null

  lateinit var coroutineScope: CoroutineScope

  @Composable
  operator fun invoke(
    content: @Composable AppConfiguration.() -> Unit,
    vararg keys: Any
  ) {

    stateValues = object : StateValues {
      override val latestNotification: NotificationDataModel? by latestInAppNotificationState.collectAsState()

      override val userAccountState: DataState<UserAccountDataModel> by kz.aita.userAccountState.value.collectAsState()
      override val userAccount: UserAccountDataModel? by kz.aita.userAccountState.payload.collectAsState()

      override val stockState: DataState<List<GoodsItemDataModel>> by kz.aita.stockState.value.collectAsState()
      override val stock: List<GoodsItemDataModel>? by kz.aita.stockState.payload.collectAsState()
      override val stockBatches: List<GoodsBatchDataModel>? by stockBatchesState.payload.collectAsState()

      override val storesState: DataState<List<StoreDataModel>> by kz.aita.storesState.value.collectAsState()
      override val stores: List<StoreDataModel>? by kz.aita.storesState.payload.collectAsState()
      override val activeStoreId: String? by kz.aita.activeStoreIdState.collectAsState()

      override val goodsCategoriesState: DataState<List<GenericGoodsCategoryDataModel>> by genericGoodsCategoriesState.value.collectAsState()
      override val goodsCategories: List<GenericGoodsCategoryDataModel>? by genericGoodsCategoriesState.payload.collectAsState()

      override val suppliersState: DataState<List<SupplierDataModel>> by kz.aita.suppliersState.value.collectAsState()
      override val suppliers: List<SupplierDataModel>? by kz.aita.suppliersState.payload.collectAsState()

      override val navigationScreensMain: List<NavigationScreenModel> by Navigation.Main.collectAsState()
      override val navigationTransactionSaleClientId: Int by Navigation.TransactionSale.ClientId.collectAsState()
      override val navigationTransactionReturnClientId: Int by Navigation.TransactionReturn.ClientId.collectAsState()
      override val navigationTransactionSupplyClientId: Int by Navigation.TransactionSupply.ClientId.collectAsState()

      override val navigationScreensTransactionSaleLeftClient1: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient1.collectAsState()
      override val navigationScreensTransactionSaleLeftClient2: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient2.collectAsState()
      override val navigationScreensTransactionSaleLeftClient3: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient3.collectAsState()
      override val navigationScreensTransactionSaleLeftClient4: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient4.collectAsState()
      override val navigationScreensTransactionSaleLeftClient5: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient5.collectAsState()

      override val navigationScreensTransactionSaleRightClient1: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient1.collectAsState()
      override val navigationScreensTransactionSaleRightClient2: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient2.collectAsState()
      override val navigationScreensTransactionSaleRightClient3: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient3.collectAsState()
      override val navigationScreensTransactionSaleRightClient4: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient4.collectAsState()
      override val navigationScreensTransactionSaleRightClient5: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient5.collectAsState()

      override val navigationScreensTransactionReturnLeftClient1: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient1.collectAsState()
      override val navigationScreensTransactionReturnLeftClient2: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient2.collectAsState()
      override val navigationScreensTransactionReturnLeftClient3: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient3.collectAsState()
      override val navigationScreensTransactionReturnLeftClient4: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient4.collectAsState()
      override val navigationScreensTransactionReturnLeftClient5: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient5.collectAsState()

      override val navigationScreensTransactionReturnRightClient1: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient1.collectAsState()
      override val navigationScreensTransactionReturnRightClient2: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient2.collectAsState()
      override val navigationScreensTransactionReturnRightClient3: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient3.collectAsState()
      override val navigationScreensTransactionReturnRightClient4: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient4.collectAsState()
      override val navigationScreensTransactionReturnRightClient5: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient5.collectAsState()

      override val navigationScreensTransactionSupplyLeftClient1: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient1.collectAsState()
      override val navigationScreensTransactionSupplyLeftClient2: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient2.collectAsState()
      override val navigationScreensTransactionSupplyLeftClient3: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient3.collectAsState()
      override val navigationScreensTransactionSupplyLeftClient4: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient4.collectAsState()
      override val navigationScreensTransactionSupplyLeftClient5: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient5.collectAsState()

      override val navigationScreensTransactionSupplyRightClient1: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient1.collectAsState()
      override val navigationScreensTransactionSupplyRightClient2: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient2.collectAsState()
      override val navigationScreensTransactionSupplyRightClient3: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient3.collectAsState()
      override val navigationScreensTransactionSupplyRightClient4: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient4.collectAsState()
      override val navigationScreensTransactionSupplyRightClient5: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient5.collectAsState()

      override val navigationScreensStockLeft: List<NavigationScreenModel> by Navigation.Stock.Left.collectAsState()
      override val navigationScreensStockRight: List<NavigationScreenModel> by Navigation.Stock.Right.collectAsState()

      override val navigationScreensMenuLeft: List<NavigationScreenModel> by Navigation.Menu.Left.collectAsState()
      override val navigationScreensMenuRight: List<NavigationScreenModel> by Navigation.Menu.Right.collectAsState()
      override val navigationScreensUserAuthLeft: List<NavigationScreenModel> by Navigation.UserAuth.Left.collectAsState()
      override val navigationScreensUserAuthRight: List<NavigationScreenModel> by Navigation.UserAuth.Right.collectAsState()

      override val globalAppConfiguration: GlobalAppConfigurationDataModel by globalAppConfigurationState.payload.collectAsState()
      override val strings: List<LocalizedStringGroupDataModel>? by stringsState.payload.collectAsState()
      override val dimensions: List<StylizedDimensionGroupDataModel>? by dimensionsState.payload.collectAsState()
      override val colors: List<StylizedColorGroupDataModel>? by colorsState.payload.collectAsState()
      override val drawables: List<StylizedDrawablePathsGroupDataModel>? by drawablesState.payload.collectAsState()

      override val appModeId: Int by appModeState.collectAsState()
      override val appLanguage: String by appLanguageState.collectAsState()
      override val appThemeId: Long by appThemeIdState.collectAsState()
      override val appSizeModeId: Long by appSizeModeIdState.collectAsState()

      override val stringAppName: String by stringAppNameState.collectAsState()
      override val stringLogIn: String by stringLogInState.collectAsState()
      override val stringPhoneNumber: String by stringPhoneNumberState.collectAsState()
      override val stringEnterPhoneNumber: String by stringEnterPhoneNumberState.collectAsState()
      override val stringEmail: String by stringEmailState.collectAsState()
      override val stringEnterEmailAddress: String by stringEnterEmailAddressState.collectAsState()
      override val stringPassword: String by stringPasswordState.collectAsState()
      override val stringEnterPassword: String by stringEnterPasswordState.collectAsState()
      override val stringCancel: String by stringCancelState.collectAsState()
      override val stringClear: String by stringClearState.collectAsState()
      override val stringAuthenticationFailed: String by stringAuthenticationFailedState.collectAsState()
      override val stringPhoneNumberMustBe: String by stringPhoneNumberMustBeState.collectAsState()
      override val stringEmailMustBe: String by stringEmailMustBeState.collectAsState()
      override val stringPasswordMustBe: String by stringPasswordMustBeState.collectAsState()
      override val stringRepeatPassword: String by stringRepeatPasswordState.collectAsState()
      override val stringPasswordsMustMatch: String by stringPasswordsMustMatchState.collectAsState()
      override val stringFirstName: String by stringFirstNameState.collectAsState()
      override val stringLastName: String by stringLastNameState.collectAsState()
      override val stringEnterFirstName: String by stringEnterFirstNameState.collectAsState()
      override val stringEnterLastName: String by stringEnterLastNameState.collectAsState()
      override val stringUserWithThisPhoneNumberIsAlreadyRegistered: String by stringUserWithThisPhoneNumberIsAlreadyRegisteredState.collectAsState()
      override val stringUserWithThisEmailAddressIsAlreadyRegistered: String by stringUserWithThisEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringSignUp: String by stringSignUpState.collectAsState()
      override val stringConfirm: String by stringConfirmState.collectAsState()
      override val stringSale: String by stringSaleState.collectAsState()
      override val stringReturn: String by stringReturnState.collectAsState()
      override val stringSupply: String by stringSupplyState.collectAsState()
      override val stringStock: String by stringStockState.collectAsState()
      override val stringMenu: String by stringMenuState.collectAsState()
      override val stringBack: String by stringBackState.collectAsState()
      override val stringAddGoodsItem: String by stringAddGoodsItemState.collectAsState()
      override val stringEditGoodsItem: String by stringEditGoodsItemState.collectAsState()
      override val stringUserAccount: String by stringUserAccountState.collectAsState()
      override val stringGoodsCategories: String by stringGoodsCategoriesState.collectAsState()
      override val stringAddGoodsCategory: String by stringAddGoodsCategoryState.collectAsState()
      override val stringEditGoodsCategory: String by stringEditGoodsCategoryState.collectAsState()
      override val stringStores: String by stringStoresState.collectAsState()
      override val stringAddStore: String by stringAddStoreState.collectAsState()
      override val stringEditStore: String by stringEditStoreState.collectAsState()
      override val stringSubscription: String by stringSubscriptionState.collectAsState()
      override val stringSubscriptionPlans: String by stringSubscriptionPlansState.collectAsState()
      override val stringTransactionHistory: String by stringTransactionHistoryState.collectAsState()
      override val stringReceipt: String by stringReceiptState.collectAsState()
      override val stringAnalytics: String by stringAnalyticsState.collectAsState()
      override val stringWorkers: String by stringWorkersState.collectAsState()
      override val stringAddWorker: String by stringAddWorkerState.collectAsState()
      override val stringEditWorker: String by stringEditWorkerState.collectAsState()
      override val stringSuppliers: String by stringSuppliersState.collectAsState()
      override val stringAddSupplier: String by stringAddSupplierState.collectAsState()
      override val stringEditSupplier: String by stringEditSupplierState.collectAsState()
      override val stringDebtors: String by stringDebtorsState.collectAsState()
      override val stringCloseDebt: String by stringCloseDebtState.collectAsState()
      override val stringDevices: String by stringDevicesState.collectAsState()
      override val stringAppLanguage: String by stringAppLanguageState.collectAsState()
      override val stringAppTheme: String by stringAppThemeState.collectAsState()
      override val stringSelect: String by stringSelectState.collectAsState()
      override val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String by stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringFirstNameCannotBeEmptyOrJustWhitespaces: String by stringFirstNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
      override val stringLastNameCannotBeEmptyOrJustWhitespaces: String by stringLastNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
      override val stringSystemLanguage: String by stringSystemLanguageState.collectAsState()
      override val stringBluetoothPermissionRequired: String by stringBluetoothPermissionRequiredState.collectAsState()
      override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
      override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings: String by stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.collectAsState()
      override val stringBluetoothDisabled: String by stringBluetoothDisabledState.collectAsState()
      override val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
      override val stringSearchByAnyData: String by stringSearchByAnyDataState.collectAsState()
      override val stringListEmpty: String by stringListEmptyState.collectAsState()
      override val stringNoMatches: String by stringNoMatchesState.collectAsState()
      override val stringName: String by stringNameState.collectAsState()
      override val stringBarcode: String by stringBarcodeState.collectAsState()
      override val stringSupplyPrice: String by stringSupplyPriceState.collectAsState()
      override val stringSalePrice: String by stringSalePriceState.collectAsState()
      override val stringReturnPrice: String by stringReturnPriceState.collectAsState()
      override val stringCategory: String by stringCategoryState.collectAsState()
      override val stringSupplier: String by stringSupplierState.collectAsState()
      override val stringEnterName: String by stringEnterNameState.collectAsState()
      override val stringEnterBarcode: String by stringEnterBarcodeState.collectAsState()
      override val stringEnterSupplyPrice: String by stringEnterSupplyPriceState.collectAsState()
      override val stringEnterSalePrice: String by stringEnterSalePriceState.collectAsState()
      override val stringEnterReturnPrice: String by stringEnterReturnPriceState.collectAsState()
      override val stringSelectCategory: String by stringSelectCategoryState.collectAsState()
      override val stringSelectSupplier: String by stringSelectSupplierState.collectAsState()
      override val stringEdit: String by stringEditState.collectAsState()
      override val stringChangePassword: String by stringChangePasswordState.collectAsState()
      override val stringNewPassword: String by stringNewPasswordState.collectAsState()
      override val stringEnterNewPassword: String by stringEnterNewPasswordState.collectAsState()
      override val stringRepeatNewPassword: String by stringRepeatNewPasswordState.collectAsState()
      override val stringConfirmationPassword: String by stringConfirmationPasswordState.collectAsState()
      override val stringRequiredToEditAccount: String by stringRequiredToEditAccountState.collectAsState()
      override val stringAccountSuccessfullyUpdated: String by stringAccountSuccessfullyUpdatedState.collectAsState()
      override val stringLoggingOut: String by stringLoggingOutState.collectAsState()
      override val stringSessionTimeExpiredLoggingOut: String by stringSessionTimeExpiredLoggingOutState.collectAsState()
      override val stringAlias: String by stringAliasState.collectAsState()
      override val stringDescription: String by stringDescriptionState.collectAsState()
      override val stringEnterAlias: String by stringEnterAliasState.collectAsState()
      override val stringEnterDescription: String by stringEnterDescriptionState.collectAsState()
      override val stringOptional: String by stringOptionalState.collectAsState()
      override val stringLoggingIn: String by stringLoggingInState.collectAsState()
      override val stringSigningUp: String by stringSigningUpState.collectAsState()
      override val stringCompanyForm: String by stringCompanyFormState.collectAsState()
      override val stringMeasurementUnit: String by stringMeasurementUnitState.collectAsState()
      override val stringNoActiveStore: String by stringNoActiveStoreState.collectAsState()
      override val stringSelectInMenu: String by stringSelectInMenuState.collectAsState()
      override val stringSupplyData: String by stringSupplyDataState.collectAsState()
      override val stringSaleData: String by stringSaleDataState.collectAsState()
      override val stringReturnData: String by stringReturnDataState.collectAsState()
      override val stringAddSupplyData: String by stringAddSupplyDataState.collectAsState()
      override val stringAddSaleData: String by stringAddSaleDataState.collectAsState()
      override val stringAddReturnData: String by stringAddReturnDataState.collectAsState()
      override val stringAddBarcode: String by stringAddBarcodeState.collectAsState()
      override val stringAddName: String by stringAddNameState.collectAsState()
      override val stringPayment: String by stringPaymentState.collectAsState()
      override val stringAll: String by stringAllState.collectAsState()
      override val stringQuick: String by stringQuickState.collectAsState()
      override val stringCategories: String by stringCategoriesState.collectAsState()
      override val stringMain: String by stringMainState.collectAsState()
      override val stringAddTranslation: String by stringAddTranslationState.collectAsState()
      override val stringSetActive: String by stringSetActiveState.collectAsState()
      override val stringOutOfStock: String by stringOutOfStockState.collectAsState()
      override val stringDelete: String by stringDeleteState.collectAsState()
      override val stringCash: String by stringCashState.collectAsState()
      override val stringCashless: String by stringCashlessState.collectAsState()
      override val stringMixed: String by stringMixedState.collectAsState()
      override val stringAdd: String by stringAddState.collectAsState()
      override val stringSubtract: String by stringSubtractState.collectAsState()
      override val stringCurrentBatchData: String by stringCurrentQuantityDataState.collectAsState()
      override val stringEnterQuantity: String by stringEnterQuantityState.collectAsState()
      override val stringAddQuantityData: String by stringAddQuantityDataState.collectAsState()
      override val stringShelfBatch: String by stringShelfBatchState.collectAsState()
      override val stringActiveStore: String by stringActiveStoreState.collectAsState()
      override val stringMakeInactive: String by stringMakeInactiveState.collectAsState()
      override val stringCartEmpty: String by stringCartEmptyState.collectAsState()
      override val stringComplete: String by stringCompleteState.collectAsState()
      override val stringNoActiveWorkshift: String by stringNoActiveWorkshiftState.collectAsState()
      override val stringCart: String by stringCartState.collectAsState()
      override val stringAppMode: String by stringAppModeState.collectAsState()
      override val stringFinances: String by stringFinancesState.collectAsState()
      override val stringItems: String by stringItemsState.collectAsState()
      override val stringBatches: String by stringBatchesState.collectAsState()
      override val stringStandardPricesForSuppliers: String by stringStandardPricesForSuppliersState.collectAsState()
      override val stringEditableForIndividualBatches: String by stringEditableForIndividualBatchesState.collectAsState()
      override val stringBatchesData: String by stringBatchesDataState.collectAsState()

      override val screenWidth: Dp by _screenWidthState.collectAsState()
      override val screenHeight: Dp by _screenHeightState.collectAsState()
      override val wideScreenMinWidth: Float by _wideScreenMinWidthState.collectAsState()
      override val boundWidgetWidth: Dp by _boundWidgetWidthState.collectAsState()
      override val isNarrowScreen: Boolean by _isNarrowScreenState.collectAsState()
      override val textSize: TextUnit by _textSizeState.collectAsState()
      override val titleTextSize: TextUnit by _titleTextSizeState.collectAsState()
      override val accentTextSize: TextUnit by _accentTextSizeState.collectAsState()
      override val smallTextSize: TextUnit by _smallTextSizeState.collectAsState()
      override val focusedBorderWidth: Dp by _focusedBorderWidthState.collectAsState()
      override val unfocusedBorderWidth: Dp by _unfocusedBorderWidthState.collectAsState()
      override val cornerRadius: Dp by _cornerRadiusState.collectAsState()
      override val iconSize: Dp by _iconSizeState.collectAsState()
      override val textFieldHeightMultiplierRelativeToTextSize: Float by _textFieldHeightMultiplierRelativeToTextSizeState.collectAsState()
      override val textFieldHeight: Dp by _textFieldHeightState.collectAsState()
      override val wideTextFieldHeight: Dp by _wideTextFieldHeightState.collectAsState()
      override val textFieldIconPadding: Dp by _textFieldIconPaddingState.collectAsState()
      override val marginTextField: Dp by _marginTextFieldState.collectAsState()
      override val marginTextFieldGroup: Dp by _marginTextFieldGroupState.collectAsState()

      override val AccentColor: Color by _AccentColorState.collectAsState()
      override val BackgroundColor: Color by _BackgroundColorState.collectAsState()
      override val TextColor: Color by _TextColorState.collectAsState()
      override val AccentTextColor: Color by _AccentTextColorState.collectAsState()
      override val PlaceholderTextColor: Color by _PlaceholderTextColorState.collectAsState()
      override val DisabledColor: Color by _DisabledColorState.collectAsState()
      override val ErrorColor: Color by _ErrorColorState.collectAsState()
      override val IconTintColor: Color by _IconTintColorState.collectAsState()
      override val OkayColor: Color by _OkayColorState.collectAsState()
      override val BorderlineBadColor: Color by _BorderlineBadColorState.collectAsState()

      override val drawablePathAITALogo: String by drawablePathAITALogoState.collectAsState()
      private val _drawableResAITALogo = MutableStateFlow(Res.drawable._0_0)
      override val drawableResAITALogo = _drawableResAITALogo.asStateFlow()

      override val drawablePathIconPassword: String by drawablePathIconPasswordState.collectAsState()
      private val _drawableResIconPassword = MutableStateFlow(Res.drawable._1_0)
      override val drawableResIconPassword = _drawableResIconPassword.asStateFlow()

      override val drawablePathIconCancel: String by drawablePathIconCancelState.collectAsState()
      private val _drawableResIconCancel = MutableStateFlow(Res.drawable._2_0)
      override val drawableResIconCancel = _drawableResIconCancel.asStateFlow()

      override val drawablePathIconEyeHide: String by drawablePathIconEyeHideState.collectAsState()
      private val _drawableResIconEyeHide = MutableStateFlow(Res.drawable._3_0)
      override val drawableResIconEyeHide = _drawableResIconEyeHide.asStateFlow()

      override val drawablePathIconEyeShow: String by drawablePathIconEyeShowState.collectAsState()
      private val _drawableResIconEyeShow = MutableStateFlow(Res.drawable._4_0)
      override val drawableResIconEyeShow = _drawableResIconEyeShow.asStateFlow()

      override val drawablePathIconEmail: String by drawablePathIconEmailState.collectAsState()
      private val _drawableResIconEmail = MutableStateFlow(Res.drawable._5_0)
      override val drawableResIconEmail = _drawableResIconEmail.asStateFlow()

      override val drawablePathIconPhone: String by drawablePathIconPhoneState.collectAsState()
      private val _drawableResIconPhone = MutableStateFlow(Res.drawable._6_0)
      override val drawableResIconPhone: StateFlow<DrawableResource> = _drawableResIconPhone.asStateFlow()

      override val drawablePathIconExpandMore: String by drawablePathIconExpandMoreState.collectAsState()
      private val _drawableResIconExpandMore = MutableStateFlow(Res.drawable._7_0)
      override val drawableResIconExpandMore: StateFlow<DrawableResource> = _drawableResIconExpandMore.asStateFlow()

      override val drawablePathIconExpandLess: String by drawablePathIconExpandLessState.collectAsState()
      private val _drawableResIconExpandLess = MutableStateFlow(Res.drawable._8_0)
      override val drawableResIconExpandLess: StateFlow<DrawableResource> = _drawableResIconExpandLess.asStateFlow()

      override val drawablePathIconPerson: String by drawablePathIconPersonState.collectAsState()
      private val _drawableResIconPerson = MutableStateFlow(Res.drawable._9_0)
      override val drawableResIconPerson: StateFlow<DrawableResource> = _drawableResIconPerson.asStateFlow()

      override val drawablePathIconTransactionSale: String by drawablePathIconTransactionSaleState.collectAsState()
      private val _drawableResIconTransactionSale = MutableStateFlow(Res.drawable._10_0)
      override val drawableResIconTransactionSale: StateFlow<DrawableResource> =
        _drawableResIconTransactionSale.asStateFlow()

      override val drawablePathIconTransactionReturn: String by drawablePathIconTransactionReturnState.collectAsState()
      private val _drawableResIconTransactionReturn = MutableStateFlow(Res.drawable._11_0)
      override val drawableResIconTransactionReturn: StateFlow<DrawableResource> =
        _drawableResIconTransactionReturn.asStateFlow()

      override val drawablePathIconTransactionSupply: String by drawablePathIconTransactionSupplyState.collectAsState()
      private val _drawableResIconTransactionSupply = MutableStateFlow(Res.drawable._12_0)
      override val drawableResIconTransactionSupply: StateFlow<DrawableResource> =
        _drawableResIconTransactionSupply.asStateFlow()

      override val drawablePathIconStock: String by drawablePathIconStockState.collectAsState()
      private val _drawableResIconStock = MutableStateFlow(Res.drawable._13_0)
      override val drawableResIconStock: StateFlow<DrawableResource> = _drawableResIconStock.asStateFlow()

      override val drawablePathIconMenu: String by drawablePathIconMenuState.collectAsState()
      private val _drawableResIconMenu = MutableStateFlow(Res.drawable._14_0)
      override val drawableResIconMenu: StateFlow<DrawableResource> = _drawableResIconMenu.asStateFlow()

      override val drawablePathIconBackArrow: String by drawablePathIconBackArrowState.collectAsState()
      private val _drawableResIconBackArrow = MutableStateFlow(Res.drawable._15_0)
      override val drawableResIconBackArrow: StateFlow<DrawableResource> = _drawableResIconBackArrow.asStateFlow()

      override val drawablePathIconAdd: String by drawablePathIconAddState.collectAsState()
      private val _drawableResIconAdd = MutableStateFlow(Res.drawable._16_0)
      override val drawableResIconAdd: StateFlow<DrawableResource> = _drawableResIconAdd.asStateFlow()

      override val drawablePathIconUserAccount: String by drawablePathIconUserAccountState.collectAsState()
      private val _drawableResIconUserAccount = MutableStateFlow(Res.drawable._17_0)
      override val drawableResIconUserAccount: StateFlow<DrawableResource> = _drawableResIconUserAccount.asStateFlow()

      override val drawablePathIconGoodsCategories: String by drawablePathIconGoodsCategoriesState.collectAsState()
      private val _drawableResIconGoodsCategories = MutableStateFlow(Res.drawable._18_0)
      override val drawableResIconGoodsCategories: StateFlow<DrawableResource> =
        _drawableResIconGoodsCategories.asStateFlow()

      override val drawablePathIconStores: String by drawablePathIconStoresState.collectAsState()
      private val _drawableResIconStores = MutableStateFlow(Res.drawable._19_0)
      override val drawableResIconStores: StateFlow<DrawableResource> = _drawableResIconStores.asStateFlow()

      override val drawablePathIconTransactionHistory: String by drawablePathIconTransactionHistoryState.collectAsState()
      private val _drawableResIconTransactionHistory = MutableStateFlow(Res.drawable._20_0)
      override val drawableResIconTransactionHistory: StateFlow<DrawableResource> =
        _drawableResIconTransactionHistory.asStateFlow()

      override val drawablePathIconAnalytics: String by drawablePathIconAnalyticsState.collectAsState()
      private val _drawableResIconAnalytics = MutableStateFlow(Res.drawable._21_0)
      override val drawableResIconAnalytics: StateFlow<DrawableResource> = _drawableResIconAnalytics.asStateFlow()

      override val drawablePathIconWorkers: String by drawablePathIconWorkersState.collectAsState()
      private val _drawableResIconWorkers = MutableStateFlow(Res.drawable._22_0)
      override val drawableResIconWorkers: StateFlow<DrawableResource> = _drawableResIconWorkers.asStateFlow()

      override val drawablePathIconSuppliers: String by drawablePathIconSuppliersState.collectAsState()
      private val _drawableResIconSuppliers = MutableStateFlow(Res.drawable._23_0)
      override val drawableResIconSuppliers: StateFlow<DrawableResource> = _drawableResIconSuppliers.asStateFlow()

      override val drawablePathIconDebtors: String by drawablePathIconDebtorsState.collectAsState()
      private val _drawableResIconDebtors = MutableStateFlow(Res.drawable._24_0)
      override val drawableResIconDebtors: StateFlow<DrawableResource> = _drawableResIconDebtors.asStateFlow()

      override val drawablePathIconDevices: String by drawablePathIconDevicesState.collectAsState()
      private val _drawableResIconDevices = MutableStateFlow(Res.drawable._25_0)
      override val drawableResIconDevices: StateFlow<DrawableResource> = _drawableResIconDevices.asStateFlow()

      override val drawablePathIconAppLanguage: String by drawablePathIconAppLanguageState.collectAsState()
      private val _drawableResIconAppLanguage = MutableStateFlow(Res.drawable._26_0)
      override val drawableResIconAppLanguage: StateFlow<DrawableResource> = _drawableResIconAppLanguage.asStateFlow()

      override val drawablePathIconAppTheme: String by drawablePathIconAppThemeState.collectAsState()
      private val _drawableResIconAppTheme = MutableStateFlow(Res.drawable._27_0)
      override val drawableResIconAppTheme: StateFlow<DrawableResource> = _drawableResIconAppTheme.asStateFlow()

      override val drawablePathIconCheck: String by drawablePathIconCheckState.collectAsState()
      private val _drawableResIconCheck = MutableStateFlow(Res.drawable._28_0)
      override val drawableResIconCheck: StateFlow<DrawableResource> = _drawableResIconCheck.asStateFlow()

      override val drawablePathIconEdit: String by drawablePathIconEditState.collectAsState()
      private val _drawableResIconEdit = MutableStateFlow(Res.drawable._29_0)
      override val drawableResIconEdit: StateFlow<DrawableResource> = _drawableResIconEdit.asStateFlow()

      override val drawablePathIconSettings: String by drawablePathIconSettingsState.collectAsState()
      private val _drawableResIconSettings = MutableStateFlow(Res.drawable._30_0)
      override val drawableResIconSettings: StateFlow<DrawableResource> = _drawableResIconSettings.asStateFlow()

      override val drawablePathIconSearch: String by drawablePathIconSearchState.collectAsState()
      private val _drawableResIconSearch = MutableStateFlow(Res.drawable._31_0)
      override val drawableResIconSearch: StateFlow<DrawableResource> = _drawableResIconSearch.asStateFlow()

      override val drawablePathIconBarcodeCamScanner: String by drawablePathIconBarcodeCamScannerState.collectAsState()
      private val _drawableResIconBarcodeCamScanner = MutableStateFlow(Res.drawable._32_0)
      override val drawableResIconBarcodeCamScanner: StateFlow<DrawableResource> =
        _drawableResIconBarcodeCamScanner.asStateFlow()

      override val drawablePathIconDelete: String by drawablePathIconDeleteState.collectAsState()
      private val _drawableResIconDelete = MutableStateFlow(Res.drawable._33_0)
      override val drawableResIconDelete: StateFlow<DrawableResource> = _drawableResIconDelete.asStateFlow()

      override val drawablePathIconExit: String by drawablePathIconExitState.collectAsState()
      private val _drawableResIconExit = MutableStateFlow(Res.drawable._34_0)
      override val drawableResIconExit: StateFlow<DrawableResource> = _drawableResIconExit.asStateFlow()

      override val drawablePathIconSwitch: String by drawablePathIconSwitchState.collectAsState()
      private val _drawableResIconSwitch = MutableStateFlow(Res.drawable._35_0)
      override val drawableResIconSwitch: StateFlow<DrawableResource> = _drawableResIconSwitch.asStateFlow()

      override val drawablePathIconCart: String by drawablePathIconCartState.collectAsState()
      private val _drawableResIconCart = MutableStateFlow(Res.drawable._36_0)
      override val drawableResIconCart: StateFlow<DrawableResource> = _drawableResIconCart.asStateFlow()

      override val drawablePathIconAddCart: String by drawablePathIconAddCartState.collectAsState()
      private val _drawableResIconAddCart = MutableStateFlow(Res.drawable._37_0)
      override val drawableResIconAddCart: StateFlow<DrawableResource> = _drawableResIconAddCart.asStateFlow()

      override val drawablePathIconSubtract: String by drawablePathIconSubtractState.collectAsState()
      private val _drawableResIconSubtract = MutableStateFlow(Res.drawable._38_0)
      override val drawableResIconSubtract: StateFlow<DrawableResource> = _drawableResIconSubtract.asStateFlow()

      override val drawablePathIconReceipt: String by drawablePathIconReceiptState.collectAsState()
      private val _drawableResIconReceipt = MutableStateFlow(Res.drawable._39_0)
      override val drawableResIconReceipt: StateFlow<DrawableResource> = _drawableResIconReceipt.asStateFlow()

      override val drawablePathIconFinances: String by drawablePathIconFinancesState.collectAsState()

      private val _drawableResIconFinances = MutableStateFlow(Res.drawable._40_0)
      override val drawableResIconFinances = _drawableResIconFinances.asStateFlow()

      override suspend fun updateDrawableResources() {
        _drawableResAITALogo.emit(if (stateValues.appThemeId == 1L) Res.drawable._0_1 else Res.drawable._0_0)

        _drawableResIconPassword.emit(if (stateValues.appThemeId == 1L) Res.drawable._1_1 else Res.drawable._1_0)

        _drawableResIconCancel.emit(if (stateValues.appThemeId == 1L) Res.drawable._2_1 else Res.drawable._2_0)

        _drawableResIconEyeHide.emit(if (stateValues.appThemeId == 1L) Res.drawable._3_1 else Res.drawable._3_0)

        _drawableResIconEyeShow.emit(if (stateValues.appThemeId == 1L) Res.drawable._4_1 else Res.drawable._4_0)

        _drawableResIconEmail.emit(if (stateValues.appThemeId == 1L) Res.drawable._5_1 else Res.drawable._5_0)

        _drawableResIconPhone.emit(if (stateValues.appThemeId == 1L) Res.drawable._6_1 else Res.drawable._6_0)

        _drawableResIconExpandMore.emit(if (stateValues.appThemeId == 1L) Res.drawable._7_1 else Res.drawable._7_0)

        _drawableResIconExpandLess.emit(if (stateValues.appThemeId == 1L) Res.drawable._8_1 else Res.drawable._8_0)

        _drawableResIconPerson.emit(if (stateValues.appThemeId == 1L) Res.drawable._9_1 else Res.drawable._9_0)

        _drawableResIconTransactionSale.emit(if (stateValues.appThemeId == 1L) Res.drawable._10_1 else Res.drawable._10_0)

        _drawableResIconTransactionReturn.emit(if (stateValues.appThemeId == 1L) Res.drawable._11_1 else Res.drawable._11_0)

        _drawableResIconTransactionSupply.emit(if (stateValues.appThemeId == 1L) Res.drawable._12_1 else Res.drawable._12_0)

        _drawableResIconStock.emit(if (stateValues.appThemeId == 1L) Res.drawable._13_1 else Res.drawable._13_0)

        _drawableResIconMenu.emit(if (stateValues.appThemeId == 1L) Res.drawable._14_1 else Res.drawable._14_0)

        _drawableResIconBackArrow.emit(if (stateValues.appThemeId == 1L) Res.drawable._15_1 else Res.drawable._15_0)

        _drawableResIconAdd.emit(if (stateValues.appThemeId == 1L) Res.drawable._16_1 else Res.drawable._16_0)

        _drawableResIconUserAccount.emit(if (stateValues.appThemeId == 1L) Res.drawable._17_1 else Res.drawable._17_0)

        _drawableResIconGoodsCategories.emit(if (stateValues.appThemeId == 1L) Res.drawable._18_1 else Res.drawable._18_0)

        _drawableResIconStores.emit(if (stateValues.appThemeId == 1L) Res.drawable._19_1 else Res.drawable._19_0)

        _drawableResIconTransactionHistory.emit(if (stateValues.appThemeId == 1L) Res.drawable._20_1 else Res.drawable._20_0)

        _drawableResIconAnalytics.emit(if (stateValues.appThemeId == 1L) Res.drawable._21_1 else Res.drawable._21_0)

        _drawableResIconWorkers.emit(if (stateValues.appThemeId == 1L) Res.drawable._22_1 else Res.drawable._22_0)

        _drawableResIconSuppliers.emit(if (stateValues.appThemeId == 1L) Res.drawable._23_1 else Res.drawable._23_0)

        _drawableResIconDebtors.emit(if (stateValues.appThemeId == 1L) Res.drawable._24_1 else Res.drawable._24_0)

        _drawableResIconDevices.emit(if (stateValues.appThemeId == 1L) Res.drawable._25_1 else Res.drawable._25_0)

        _drawableResIconAppLanguage.emit(if (stateValues.appThemeId == 1L) Res.drawable._26_1 else Res.drawable._26_0)

        _drawableResIconAppTheme.emit(if (stateValues.appThemeId == 1L) Res.drawable._27_1 else Res.drawable._27_0)

        _drawableResIconCheck.emit(if (stateValues.appThemeId == 1L) Res.drawable._28_1 else Res.drawable._28_0)

        _drawableResIconEdit.emit(if (stateValues.appThemeId == 1L) Res.drawable._29_1 else Res.drawable._29_0)

        _drawableResIconSettings.emit(if (stateValues.appThemeId == 1L) Res.drawable._30_1 else Res.drawable._30_0)

        _drawableResIconSearch.emit(if (stateValues.appThemeId == 1L) Res.drawable._31_1 else Res.drawable._31_0)

        _drawableResIconBarcodeCamScanner.emit(if (stateValues.appThemeId == 1L) Res.drawable._32_1 else Res.drawable._32_0)

        _drawableResIconDelete.emit(if (stateValues.appThemeId == 1L) Res.drawable._33_1 else Res.drawable._33_0)

        _drawableResIconExit.emit(if (stateValues.appThemeId == 1L) Res.drawable._34_1 else Res.drawable._34_0)

        _drawableResIconSwitch.emit(if (stateValues.appThemeId == 1L) Res.drawable._35_1 else Res.drawable._35_0)

        _drawableResIconCart.emit(if (stateValues.appThemeId == 1L) Res.drawable._36_1 else Res.drawable._36_0)

        _drawableResIconAddCart.emit(if (stateValues.appThemeId == 1L) Res.drawable._37_1 else Res.drawable._37_0)

        _drawableResIconSubtract.emit(if (stateValues.appThemeId == 1L) Res.drawable._38_1 else Res.drawable._38_0)

        _drawableResIconReceipt.emit(if (stateValues.appThemeId == 1L) Res.drawable._39_1 else Res.drawable._39_0)
        _drawableResIconFinances.emit(if (stateValues.appThemeId == 1L) Res.drawable._40_1 else Res.drawable._40_0)
      }
    }

    softKeyboardController = LocalSoftwareKeyboardController.current
    coroutineScope = rememberCoroutineScope()

    key(keys) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
      ) {
        content()

        coroutineScope.launch(Dispatchers.ourIo) {
          _screenWidthState.emit(maxWidth)
          _screenHeightState.emit(maxHeight)
          _isNarrowScreenState.emit(maxWidth.value < stateValues.wideScreenMinWidth)

          val resourceStrings = loadResourceStrings()
          val resourceDimensions = loadResourceDimensions()
          val resourceColors = loadResourceColors()
          val resourceDrawables = loadResourceDrawablePaths()

          updateStrings(
            strings = stringsState.payloadValue ?: resourceStrings,
            resourceStrings = resourceStrings
          )

          updateDimensions(
            dimensions = dimensionsState.payloadValue ?: resourceDimensions,
            resourceDimensions = resourceDimensions
          )

          updateColors(
            colors = colorsState.payloadValue ?: resourceColors,
            resourceColors = resourceColors
          )

          updateDrawables(
            drawables = drawablesState.payloadValue ?: resourceDrawables,
            resourceDrawables = resourceDrawables
          )

          stateValues.updateDrawableResources()

          launch(Dispatchers.ourIo) {
            _isNarrowScreenState
              .collect {
                Navigation.TransactionSale.init(it)
                Navigation.TransactionReturn.init(it)
                Navigation.TransactionSupply.init(it)
                Navigation.Stock.init(it)
                Navigation.Menu.init(it)
                Navigation.UserAuth.init(it)
              }
          }

          launch(Dispatchers.ourIo) {
            stringsState
              .payload
              .collect {
                it?.let {
                  updateStrings(it, resourceStrings)
                }
              }
          }

          launch(Dispatchers.ourIo) {
            appLanguageState
              .collect {
                stringsState
                  .payloadValue?.let {
                    updateStrings(it, resourceStrings)
                  }
              }
          }

          launch(Dispatchers.ourIo) {
            dimensionsState
              .payload
              .collect {
                it?.let {
                  updateDimensions(it, resourceDimensions)
                }
              }
          }

          launch(Dispatchers.ourIo) {
            colorsState
              .payload
              .collect {
                it?.let {
                  updateColors(it, resourceColors)
                }
              }
          }

          launch(Dispatchers.ourIo) {
            drawablesState
              .payload
              .collect {
                it?.let {
                  updateDrawables(it, resourceDrawables)
                  stateValues.updateDrawableResources()
                }
              }
          }

          launch(Dispatchers.ourIo) {
            appThemeIdState
              .collect {
                colorsState
                  .payloadValue?.let {
                    updateColors(it, resourceColors)
                  }

                drawablesState
                  .payloadValue?.let {
                    updateDrawables(it, resourceDrawables)
                  }
              }
          }

          launch(Dispatchers.ourIo) {
            appSizeModeIdState
              .collect {
                dimensionsState
                  .payloadValue?.let {
                    updateDimensions(it, resourceDimensions)
                  }

              }
          }
        }
      }
    }
  }

  private suspend fun updateDimensions(
    dimensions: List<StylizedDimensionGroupDataModel>,
    resourceDimensions: List<StylizedDimensionGroupDataModel>
  ) {
    _wideScreenMinWidthState.emit(
      dimensions.extractValue(4, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        4,
        stateValues.appSizeModeId
      )!!
    )
    _boundWidgetWidthState.emit(
      (dimensions.extractValue(9, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        9,
        stateValues.appSizeModeId
      )!!).dp
    )

    _textSizeState.emit(
      (dimensions.extractValue(0, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        0,
        stateValues.appSizeModeId
      )!!).sp
    )
    _titleTextSizeState.emit(
      (dimensions.extractValue(1, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        1,
        stateValues.appSizeModeId
      )!!).sp
    )
    _accentTextSizeState.emit(
      (dimensions.extractValue(2, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        2,
        stateValues.appSizeModeId
      )!!).sp
    )
    _smallTextSizeState.emit(
      (dimensions.extractValue(3, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        3,
        stateValues.appSizeModeId
      )!!).sp
    )

    _focusedBorderWidthState.emit(
      (dimensions.extractValue(5, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        5,
        stateValues.appSizeModeId
      )!!).dp
    )
    _unfocusedBorderWidthState.emit(
      (dimensions.extractValue(6, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        6,
        stateValues.appSizeModeId
      )!!).dp
    )

    _cornerRadiusState.emit(
      (dimensions.extractValue(7, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        7,
        stateValues.appSizeModeId
      )!!).dp
    )
    _iconSizeState.emit(
      (dimensions.extractValue(8, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        8,
        stateValues.appSizeModeId
      )!!).dp
    )
    _textFieldHeightMultiplierRelativeToTextSizeState.emit(
      dimensions.extractValue(10, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        10,
        stateValues.appSizeModeId
      )!!
    )
    _textFieldHeightState.emit((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value).dp)
    _textFieldIconPaddingState.emit(
      (dimensions.extractValue(11, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        11,
        stateValues.appSizeModeId
      )!!).dp
    )
  }

  private suspend fun updateColors(
    colors: List<StylizedColorGroupDataModel>,
    resourceColors: List<StylizedColorGroupDataModel>
  ) {
    _AccentColorState.emit(
      (colors.extractColor(0, appThemeIdState.value) ?: resourceColors.extractColor(
        0,
        appThemeIdState.value
      )!!).toColor()
    )
    _BackgroundColorState.emit(
      (colors.extractColor(1, appThemeIdState.value) ?: resourceColors.extractColor(
        1,
        appThemeIdState.value
      )!!).toColor()
    )
    _TextColorState.emit(
      (colors.extractColor(2, appThemeIdState.value) ?: resourceColors.extractColor(
        2,
        appThemeIdState.value
      )!!).toColor()
    )
    _AccentTextColorState.emit(
      (colors.extractColor(3, appThemeIdState.value) ?: resourceColors.extractColor(
        3,
        appThemeIdState.value
      )!!).toColor()
    )
    _PlaceholderTextColorState.emit(
      (colors.extractColor(4, appThemeIdState.value) ?: resourceColors.extractColor(
        4,
        appThemeIdState.value
      )!!).toColor()
    )
    _DisabledColorState.emit(
      (colors.extractColor(5, appThemeIdState.value) ?: resourceColors.extractColor(
        5,
        appThemeIdState.value
      )!!).toColor()
    )
    _ErrorColorState.emit(
      (colors.extractColor(6, appThemeIdState.value) ?: resourceColors.extractColor(
        6,
        appThemeIdState.value
      )!!).toColor()
    )
    _IconTintColorState.emit(
      (colors.extractColor(7, appThemeIdState.value) ?: resourceColors.extractColor(
        7,
        appThemeIdState.value
      )!!).toColor()
    )
    _OkayColorState.emit(
      (colors.extractColor(8, appThemeIdState.value) ?: resourceColors.extractColor(
        8,
        appThemeIdState.value
      )!!).toColor()
    )
    _BorderlineBadColorState.emit(
      (colors.extractColor(9, appThemeIdState.value) ?: resourceColors.extractColor(
        9,
        appThemeIdState.value
      )!!).toColor()
    )
  }
}

@Composable
fun AppConfiguration.countrySelectionPhoneNumberTextField(
  countries: List<CountryDataModel> = stateValues.globalAppConfiguration.countries,
  valueInitial: String? = null,
  stateHost: StateHost,
  stateKey: String,
  lockedId: String? = null,
  titleText: String = stateValues.stringPhoneNumber,
  placeholderText: String = stateValues.stringEnterPhoneNumber,
  imeWithAction: ImeWithAction? = null
): DomainSelectionTextFieldContent {

  return domainSelectionTextField(
    domains = emptyList(),
    secondaryDomains = countries.map {
      SelectableDomain(
        id = "+${it.phoneNumberCode}",
        displayId = "+${it.phoneNumberCode}".toLocalizedSingleMain(),
        name = it.name,
        iconPath = it.flagDrawablePath,
        iconRes = it.mapIconRes()
      )
    },
    lockedSecondaryDomainId = lockedId?.let { if (it.startsWith("+")) it else "+$it" },
    valueInitial = valueInitial?.run {
      countries
        .find { startsWith(it.phoneNumberCode) || startsWith("+${it.phoneNumberCode}") }
        ?.takeIf { countryMatch ->
          length > countryMatch.phoneNumberSize
        }?.let { countryMatch ->
          removePrefix("+").substringAfter(countryMatch.phoneNumberCode)
        } ?: this
    },
    titleText = titleText,
    stateHost = stateHost,
    stateKey = stateKey,
    placeholderText = placeholderText,
    keyboardType = KeyboardType.Phone,
    imeWithAction = imeWithAction,
    contentInvalidText = stateValues.stringPhoneNumberMustBe,
    onContentValidityCheck = { text, _, selectedSecondaryId ->
      countries.find { "+${it.phoneNumberCode}" == selectedSecondaryId }?.run {
        text.checkAsPhoneNumber(this)
      } == true
    },
    onFilterValue = { text, _, selectedSecondaryId ->
      countries.find { "+${it.phoneNumberCode}" == selectedSecondaryId }?.run {
        text.filterAsPhoneNumber(this)
      } == true
    }
  )
}

@Composable
fun AppConfiguration.AppThemeSettingsItemWidget(
  id: Long,
  name: String,
  isActive: Boolean
) {
  Row(
    modifier = Modifier
      .height(42.dp)
      .fillMaxWidth()
      .clickable(
        interactionSource = remember {
          MutableInteractionSource()
        },
        indication = ripple(color = stateValues.TextColor),
        onClick = {
          coroutineScope.launch {
            setAppTheme(id)
          }
        }
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = name,
      modifier = Modifier
        .padding(12.dp),
      fontSize = stateValues.textSize,
      color = if (isActive)
        stateValues.AccentColor
      else
        stateValues.TextColor,
      fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
    )

    if (isActive) {
      Row {
        val drawableResIconCheck by stateValues.drawableResIconCheck.collectAsState()

        CpImage(
          modifier = Modifier
            .padding(stateValues.textFieldIconPadding)
            .fillMaxHeight()
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
          url = stateValues.drawablePathIconCheck,
          fallbackRes = drawableResIconCheck,
          contentDescription = name,
          tintColor = stateValues.AccentColor
        )

        Spacer(modifier = Modifier.width(12.dp))
      }
    }

  }
}

@Composable
fun AppConfiguration.AppLanguageSettingsItemWidget(
  language: String,
  name: String,
  flagDrawablePath: String,
  flagDrawableRes: DrawableResource,
  isActive: Boolean
) {
  Row(
    modifier = Modifier
      .height(42.dp)
      .fillMaxWidth()
      .clickable(
        interactionSource = remember {
          MutableInteractionSource()
        },
        indication = ripple(color = stateValues.TextColor),
        onClick = {
          coroutineScope.launch {
            setAppLocale(language)
          }
        }
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row {
      CpImage(
        modifier = Modifier
          .padding(stateValues.textFieldIconPadding)
          .fillMaxHeight()
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        url = flagDrawablePath,
        fallbackRes = flagDrawableRes,
        contentDescription = name
      )

      Text(
        text = name,
        modifier = Modifier
          .padding(12.dp),
        fontSize = stateValues.textSize,
        color = if (isActive)
          stateValues.AccentColor
        else
          stateValues.TextColor,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
      )
    }

    if (isActive) {
      Row {
        val iconRes by stateValues.drawableResIconCheck.collectAsState()

        CpImage(
          modifier = Modifier
            .padding(stateValues.textFieldIconPadding)
            .fillMaxHeight()
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
          url = stateValues.drawablePathIconCheck,
          fallbackRes = iconRes,
          contentDescription = name,
          tintColor = stateValues.AccentColor
        )

        Spacer(modifier = Modifier.width(12.dp))
      }
    }
  }
}

@Composable
fun AppConfiguration.actionButton(
  modifier: Modifier = Modifier,
  fillMaxHeight: Boolean = false,
  fillMaxWidthIfTextPresent: Boolean = true,

  enabled: Boolean = true,

  enabledColor: Color = stateValues.AccentColor,
  disabledColor: Color = stateValues.DisabledColor,

  text: String,
  textColor: Color = stateValues.AccentTextColor,
  textSize: TextUnit = stateValues.textSize,

  subText: String = "",
  subTextColor: Color = textColor,
  subTextSize: TextUnit = stateValues.smallTextSize,

  cornerRadius: Dp = stateValues.cornerRadius,

  icon: @Composable (() -> Unit)? = null,

  iconPath: String? = null,
  iconRes: DrawableResource? = null,
  iconContentDescription: String = text,
  iconTintColor: Color = stateValues.AccentTextColor,

  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit
): ActionButtonContent {
  var isEnabled by rememberSaveable {
    mutableStateOf(enabled)
  }

  LaunchedEffect(enabled) {
    isEnabled = enabled
  }

  val backgroundColor by animateColorAsState(
    targetValue = if (isEnabled) enabledColor else disabledColor
  )

  val textPresent =
    text.isNotEmpty() && text.isNotBlank()

  val subTextPresent =
    subText.isNotEmpty() && subText.isNotBlank()

  val textHeight = if (!textPresent) 0f else textSize.value
  val subTextHeight = if (subTextPresent) subTextSize.value else 0f

  val height = (textHeight + subTextHeight + 24).dp

  Row(
    modifier = modifier
      .run {
        if (!textPresent || !fillMaxWidthIfTextPresent)
          wrapContentWidth()
        else
          fillMaxWidth()
      }
      .run {
        if (fillMaxHeight)
          fillMaxHeight()
        else
          height(height)
      }
      .clip(RoundedCornerShape(cornerRadius))
      .background(backgroundColor)
      .run {
        if (isEnabled)
          clickable(
            onClick = onClick,
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = textColor)
          )
        else
          this
      }
      .run {
        onLongClick?.let {
          pointerInput(Unit) {
            detectTapGestures(
              onLongPress = {
                it()
              }
            )
          }
        } ?: this
      },
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    icon?.invoke() ?: iconPath?.run {
      CpImage(
        modifier = Modifier
          .fillMaxHeight()
          .padding(2.dp)
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        url = iconPath,
        fallbackRes = iconRes,
        contentDescription = iconContentDescription,
        tintColor = iconTintColor
      )
    }

    Column(
      verticalArrangement = Arrangement.Center
    ) {
      if (textPresent) {
        Text(
          modifier = Modifier.run {
            if (!fillMaxWidthIfTextPresent)
              wrapContentWidth().padding(horizontal = height / 2)
            else
              fillMaxWidth()
          },
          text = text,
          color = textColor,
          fontWeight = FontWeight.Bold,
          fontSize = textSize,
          textAlign = TextAlign.Center
        )

        if (subTextPresent) {
          Text(
            modifier = Modifier.run {
              if (!fillMaxWidthIfTextPresent)
                wrapContentWidth().padding(horizontal = height / 2)
              else
                fillMaxWidth()
            },
            text = subText,
            color = subTextColor,
            fontSize = subTextSize,
            textAlign = TextAlign.Center
          )
        }
      }

    }
  }

  return ActionButtonContent(isEnabled)
}

data class ActionButtonContent(
  var enabled: Boolean
)
