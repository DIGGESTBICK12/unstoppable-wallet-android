package io.horizontalsystems.bankwallet.modules.swapx

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.bankwallet.core.BaseFragment
import io.horizontalsystems.bankwallet.core.slideFromBottom
import io.horizontalsystems.bankwallet.core.slideFromRight
import io.horizontalsystems.bankwallet.modules.swap.SwapActionState
import io.horizontalsystems.bankwallet.modules.swap.approve.SwapApproveModule
import io.horizontalsystems.bankwallet.modules.swap.approve.confirmation.SwapApproveConfirmationModule
import io.horizontalsystems.bankwallet.modules.swap.confirmation.oneinch.OneInchConfirmationModule
import io.horizontalsystems.bankwallet.modules.swap.ui.*
import io.horizontalsystems.bankwallet.modules.swap.uniswap.PriceImpact
import io.horizontalsystems.bankwallet.modules.swapx.SwapXMainModule.ProviderTradeData
import io.horizontalsystems.bankwallet.modules.swapx.allowance.SwapAllowanceViewModelX
import io.horizontalsystems.bankwallet.ui.compose.ComposeAppTheme
import io.horizontalsystems.bankwallet.ui.compose.Keyboard.Opened
import io.horizontalsystems.bankwallet.ui.compose.TranslatableString
import io.horizontalsystems.bankwallet.ui.compose.components.*
import io.horizontalsystems.bankwallet.ui.compose.observeKeyboardState
import io.horizontalsystems.bankwallet.ui.extensions.BottomSheetHeader
import io.horizontalsystems.core.findNavController
import io.horizontalsystems.core.getNavigationResult
import io.horizontalsystems.marketkit.models.*
import kotlinx.coroutines.launch

class SwapXMainFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val factory = SwapXMainModule.Factory(requireArguments())
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
            )
            setContent {
                ComposeAppTheme {
                    SwapNavHost(
                        findNavController(),
                        factory
                    )
                }
            }
        }
    }
}

@Composable
private fun SwapNavHost(
    fragmentNavController: NavController,
    factory: SwapXMainModule.Factory,
    mainViewModel: SwapXMainViewModel = viewModel(factory = factory),
    allowanceViewModel: SwapAllowanceViewModelX = viewModel(factory = factory),
) {
    SwapMainScreen(
        navController = fragmentNavController,
        viewModel = mainViewModel,
        allowanceViewModel = allowanceViewModel,
        onCloseClick = { fragmentNavController.popBackStack() },
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwapMainScreen(
    navController: NavController,
    viewModel: SwapXMainViewModel,
    allowanceViewModel: SwapAllowanceViewModelX,
    onCloseClick: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val modalBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val providerViewItems = viewModel.swapState.providerViewItems

    ComposeAppTheme {
        ModalBottomSheetLayout(
            sheetState = modalBottomSheetState,
            sheetBackgroundColor = ComposeAppTheme.colors.transparent,
            sheetContent = {
                BottomSheetProviderSelector(
                    items = providerViewItems,
                    onSelect = { viewModel.setProvider(it) }
                ) {
                    coroutineScope.launch {
                        modalBottomSheetState.hide()
                    }
                }
            },
        ) {
            Column(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
                AppBar(
                    title = TranslatableString.ResString(R.string.Swap),
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.ResString(R.string.Button_Close),
                            icon = R.drawable.ic_close,
                            onClick = onCloseClick
                        )
                    )
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    TopMenu(
                        viewModel = viewModel,
                        showProviderSelector = { coroutineScope.launch { modalBottomSheetState.show() } },
                        onSettingsClick = { navController.slideFromBottom(it) }
                    )
                    SwapCards(
                        navController = navController,
                        viewModel = viewModel,
                        allowanceViewModel = allowanceViewModel,
                    )
                }
            }
        }
    }
}

@Composable
fun SwapCards(
    navController: NavController,
    viewModel: SwapXMainViewModel,
    allowanceViewModel: SwapAllowanceViewModelX
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardState by observeKeyboardState()
    var showSuggestions by remember { mutableStateOf(false) }

    val fromState = viewModel.swapState.fromState
    val toState = viewModel.swapState.toState
    val availableBalance = viewModel.swapState.availableBalance
    val swapError = viewModel.swapState.error
    val tradeView = viewModel.swapState.tradeView
    val tradePriceExpiration = viewModel.swapState.tradePriceExpiration
    val buttons = viewModel.swapState.buttons
    val hasNonZeroBalance = viewModel.swapState.hasNonZeroBalance

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            VSpacer(12.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ComposeAppTheme.colors.lawrence)
            ) {

                SwapXCoinCardView(
                    dex = viewModel.swapState.dex,
                    cardState = fromState,
                    navController = navController,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp),
                    focusRequester = focusRequester,
                    onCoinSelect = { viewModel.onSelectFromCoin(it) },
                    onAmountChange = { viewModel.onFromAmountChange(it) },
                ) { isFocused ->
                    showSuggestions = isFocused
                }

                VSpacer(8.dp)
                SwitchCoinsSection { viewModel.onTapSwitch() }
                VSpacer(8.dp)

                SwapXCoinCardView(
                    dex = viewModel.swapState.dex,
                    cardState = toState,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
                    navController = navController,
                    onCoinSelect = { viewModel.onSelectToCoin(it) },
                    onAmountChange = { viewModel.onToAmountChange(it) },
                )
            }

            if (swapError != null) {
                SwapError(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), text = swapError)
            } else {
                val infoItems = mutableListOf<@Composable () -> Unit>()
                when (val data = tradeView?.providerTradeData) {
                    is ProviderTradeData.OneInchTradeViewItem -> {
                        data.primaryPrice?.let { primaryPrice ->
                            data.secondaryPrice?.let { secondaryPrice ->
                                infoItems.add { Price(primaryPrice, secondaryPrice, tradePriceExpiration ?: 1f, tradeView.expired) }
                            }
                        }
                    }

                    is ProviderTradeData.UniswapTradeViewItem -> {
                        data.primaryPrice?.let { primaryPrice ->
                            data.secondaryPrice?.let { secondaryPrice ->
                                infoItems.add { Price(primaryPrice, secondaryPrice, tradePriceExpiration ?: 1f, tradeView.expired) }
                            }
                        }
                        if (allowanceViewModel.uiState.isVisible && !allowanceViewModel.uiState.revokeRequired) {
                            infoItems.add { SwapAllowance(allowanceViewModel, navController) }
                        }
                        data.priceImpact?.let {
                            infoItems.add { PriceImpact(it, navController) }
                        }
                    }

                    else -> {}
                }

                if (infoItems.isEmpty()) {
                    availableBalance?.let { infoItems.add { AvailableBalance(it) } }
                }

                if (infoItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleLineGroup(infoItems)
                }
            }

            if (buttons.revoke is SwapActionState.Enabled && allowanceViewModel.uiState.revokeRequired) {
                Spacer(modifier = Modifier.height(12.dp))
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.Approve_RevokeAndApproveInfo, allowanceViewModel.uiState.allowance ?: "")
                )
            }

            VSpacer(32.dp)

            ActionButtons(
                buttons = buttons,
                onTapRevoke = {
                    navController.getNavigationResult(SwapApproveModule.requestKey) {
                        if (it.getBoolean(SwapApproveModule.resultKey)) {
                            viewModel.didApprove()
                        }
                    }

                    viewModel.revokeEvmData?.let { revokeEvmData ->
                        navController.slideFromBottom(
                            R.id.swapApproveConfirmationFragment,
                            SwapApproveConfirmationModule.prepareParams(revokeEvmData, viewModel.dex.blockchainType, false)
                        )
                    }
                },
                onTapApprove = {
                    navController.getNavigationResult(SwapApproveModule.requestKey) {
                        if (it.getBoolean(SwapApproveModule.resultKey)) {
                            viewModel.didApprove()
                        }
                    }

                    viewModel.approveData?.let { data ->
                        navController.slideFromBottom(
                            R.id.swapApproveFragment,
                            SwapApproveModule.prepareParams(data)
                        )
                    }
                },
                onTapProceed = {
                    when (val swapData = viewModel.proceedParams) {
                        is SwapXMainModule.SwapData.OneInchData -> {
                            navController.slideFromRight(
                                R.id.oneInchConfirmationFragment,
                                OneInchConfirmationModule.prepareParams(swapData.data)
                            )
                        }

                        is SwapXMainModule.SwapData.UniswapData -> {
                            viewModel.getSendEvmData(swapData)?.let { sendEvmData ->
                                navController.slideFromBottom(
                                    R.id.swapApproveConfirmationFragment,
                                    SwapApproveConfirmationModule.prepareParams(sendEvmData, viewModel.dex.blockchainType, true)
                                )
                            }
                        }

                        null -> {}
                    }
                }
            )
        }

        VSpacer(32.dp)
        if (hasNonZeroBalance == true && fromState.inputState.amount.isEmpty() && showSuggestions && keyboardState == Opened) {
            SuggestionsBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                focusManager.clearFocus()
                viewModel.onSetAmountInBalancePercent(it)
            }
        }
    }
}


@Composable
private fun TopMenu(
    viewModel: SwapXMainViewModel,
    showProviderSelector: () -> Unit,
    onSettingsClick: (Int) -> Unit
) {
    val state = viewModel.swapState
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f)) {
            ButtonSecondaryTransparent(
                title = state.dex.provider.title,
                iconRight = R.drawable.ic_down_arrow_20,
                onClick = showProviderSelector
            )
        }
        ButtonSecondaryToggle(
            modifier = Modifier.padding(end = 16.dp),
            select = state.amountTypeSelect,
            onSelect = {
                viewModel.onToggleAmountType()
            },
            enabled = state.amountTypeSelectEnabled
        )
        ButtonSecondaryCircle(
            icon = R.drawable.ic_manage_2,
            onClick = {
                val destination = when (state.dex.provider) {
                    SwapXMainModule.OneInchProvider -> R.id.oneinchSettingsFragment
                    else -> R.id.uniswapSettingsFragment
                }
                onSettingsClick.invoke(destination)
            }
        )
    }
}

@Composable
private fun BottomSheetProviderSelector(
    items: List<SwapXMainModule.ProviderViewItem>,
    onSelect: (SwapXMainModule.ISwapProvider) -> Unit,
    onCloseClick: () -> Unit
) {
    val context = LocalContext.current
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_swap_24),
        title = stringResource(R.string.Swap_SelectSwapProvider_Title),
        onCloseClick = onCloseClick,
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob)
    ) {
        Spacer(Modifier.height(12.dp))
        CellUniversalLawrenceSection(items, showFrame = true) { item ->
            RowUniversal(
                onClick = {
                    onSelect.invoke(item.provider)
                    onCloseClick.invoke()
                },
            ) {
                Image(
                    modifier = Modifier.padding(horizontal = 16.dp).size(32.dp),
                    painter = painterResource(
                        id = getDrawableResource(item.provider.id, context)
                            ?: R.drawable.coin_placeholder
                    ),
                    contentDescription = null
                )
                body_leah(
                    modifier = Modifier.weight(1f),
                    text = item.provider.title
                )
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.selected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_checkmark_20),
                            tint = ComposeAppTheme.colors.jacob,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(44.dp))
    }
}

private fun getDrawableResource(name: String, context: Context): Int? {
    val resourceId = context.resources.getIdentifier(name, "drawable", context.packageName)
    return if (resourceId == 0) null else resourceId
}
