package kz.aita

import aita.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/** Semantic, locale-independent artwork shared by all section/filter selectors. */
enum class AitaTabIcon(val family: Int) {
    All(149),
    Quick(150),
    Stock(151),
    Fresh(152),
    Popular(153),
    Recent(154),
    Restock(155),
    LowStock(156),
    Expiring(157),
    Slow(158),
    Money(159),
    Cash(160),
    Card(161),
    Mixed(162),
    Name(163),
    Price(164),
    Quantity(165),
    Ascending(166),
    Descending(167),
    Calendar(168),
    Clock(169),
    Person(170),
    Work(171),
    Workers(172),
    Invite(173),
    Requests(174),
    Responses(175),
    Remove(176),
    Roles(177),
    Store(178),
    Branches(179),
    Contract(180),
    Payment(181),
    Receipt(182),
    Wallet(183),
    Topup(184),
    Orders(185),
    Truck(186),
    Settings(187),
    Security(188),
    Phone(189),
    Email(190),
    Password(191),
    Chat(192),
    Help(193),
    Metrics(194),
    Checklist(195),
    Search(196),
    Edit(197),
    Check(198),
    Cancel(199),
    Warning(200),
    Inbox(201),
    Factory(202),
    Plan(203),
    Recover(204),
    Compare(205),
    Basket(206),
    Info(207),
    Barcode(208),
    CashRegister(209)
}

internal fun aitaTabIconForId(id: String): AitaTabIcon = when (id) {
    "all", "generic", "overview", "category" -> AitaTabIcon.All
    "quick" -> AitaTabIcon.Quick
    "in_stock", "stock", "allocation", "products", "goods_item" -> AitaTabIcon.Stock
    "fresh" -> AitaTabIcon.Fresh
    "popular", "rankings" -> AitaTabIcon.Popular
    "recent", "history", "activity", "audit", "added", "created" -> AitaTabIcon.Recent
    "restock" -> AitaTabIcon.Restock
    "low_stock" -> AitaTabIcon.LowStock
    "expiring" -> AitaTabIcon.Expiring
    "slow", "slow_moving" -> AitaTabIcon.Slow
    "amount", "return_amount", "estimate", "lowest_items", "commercial" -> AitaTabIcon.Money
    "0", "cash", "sales" -> AitaTabIcon.Cash
    "1", "card", "cashless" -> AitaTabIcon.Card
    "2", "mixed" -> AitaTabIcon.Mixed
    "name", "title", "label" -> AitaTabIcon.Name
    "price", "offers", "terms" -> AitaTabIcon.Price
    "quantity", "return_quantity" -> AitaTabIcon.Quantity
    "asc" -> AitaTabIcon.Ascending
    "desc" -> AitaTabIcon.Descending
    "today", "7", "30", "week", "month", "year", "days", "custom", "plan" -> AitaTabIcon.Calendar
    "hours" -> AitaTabIcon.Clock
    "account", "profiles", "mine", "owner" -> AitaTabIcon.Person
    "my_work", "work", "managed" -> AitaTabIcon.Work
    "workers", "store_workers", "partners", "unassigned" -> AitaTabIcon.Workers
    "invite", "invites", "invitations", "apply", "signin" -> AitaTabIcon.Invite
    "requests", "new_order", "open" -> AitaTabIcon.Requests
    "responses", "outcome" -> AitaTabIcon.Responses
    "remove", "removals" -> AitaTabIcon.Remove
    "roles" -> AitaTabIcon.Roles
    "shops", "store", "owned", "storefront", "current", "one_shop" -> AitaTabIcon.Store
    "parent", "two_shops" -> AitaTabIcon.Branches
    "contracts", "agreements", "acceptance" -> AitaTabIcon.Contract
    "paid", "pay", "billing" -> AitaTabIcon.Payment
    "receipt", "invoices", "charges" -> AitaTabIcon.Receipt
    "balance" -> AitaTabIcon.Wallet
    "top_up", "extract" -> AitaTabIcon.Topup
    "orders", "list", "listings", "basket_orders" -> AitaTabIcon.Orders
    "handoff", "followup", "handoff_flow" -> AitaTabIcon.Truck
    "technical", "system", "operations", "actions", "set" -> AitaTabIcon.Settings
    "verification", "readiness", "guard", "gate", "health", "authenticator-code" -> AitaTabIcon.Security
    "phone" -> AitaTabIcon.Phone
    "email", "email-code", "code" -> AitaTabIcon.Email
    "password", "sessions" -> AitaTabIcon.Password
    "chat", "agent" -> AitaTabIcon.Chat
    "faq", "script" -> AitaTabIcon.Help
    "revenue", "metrics", "performance", "demand" -> AitaTabIcon.Metrics
    "checklist", "closeout", "closure", "closed", "close", "kept" -> AitaTabIcon.Checklist
    "desk" -> AitaTabIcon.Search
    "edit", "changes", "custom_period" -> AitaTabIcon.Edit
    "positive", "applied" -> AitaTabIcon.Check
    "negative", "rejected", "cancelled" -> AitaTabIcon.Cancel
    "attention", "exception", "risk", "backorders" -> AitaTabIcon.Warning
    "unread", "neutral", "unchanged", "general" -> AitaTabIcon.Inbox
    "manufacturers", "suppliers", "supplier" -> AitaTabIcon.Factory
    "plans", "promise", "promises", "wave", "waves", "next_moves", "flow" -> AitaTabIcon.Plan
    "recovery", "return", "returns" -> AitaTabIcon.Recover
    "replace", "comparison" -> AitaTabIcon.Compare
    "basket" -> AitaTabIcon.Basket
    "info" -> AitaTabIcon.Info
    "auto", "tspl", "zpl", "cpcl" -> AitaTabIcon.Barcode
    "cashregister", "cashRegister", "cash_register" -> AitaTabIcon.CashRegister
    else -> AitaTabIcon.Info
}

internal fun AppConfiguration.tabIconResource(icon: AitaTabIcon): DrawableResource {
    val dark = isDarkAppTheme(stateValues.appThemeId)
    return when(icon) {
        AitaTabIcon.All -> if (dark) Res.drawable._149_1 else Res.drawable._149_0
        AitaTabIcon.Quick -> if (dark) Res.drawable._150_1 else Res.drawable._150_0
        AitaTabIcon.Stock -> if (dark) Res.drawable._151_1 else Res.drawable._151_0
        AitaTabIcon.Fresh -> if (dark) Res.drawable._152_1 else Res.drawable._152_0
        AitaTabIcon.Popular -> if (dark) Res.drawable._153_1 else Res.drawable._153_0
        AitaTabIcon.Recent -> if (dark) Res.drawable._154_1 else Res.drawable._154_0
        AitaTabIcon.Restock -> if (dark) Res.drawable._155_1 else Res.drawable._155_0
        AitaTabIcon.LowStock -> if (dark) Res.drawable._156_1 else Res.drawable._156_0
        AitaTabIcon.Expiring -> if (dark) Res.drawable._157_1 else Res.drawable._157_0
        AitaTabIcon.Slow -> if (dark) Res.drawable._158_1 else Res.drawable._158_0
        AitaTabIcon.Money -> if (dark) Res.drawable._159_1 else Res.drawable._159_0
        AitaTabIcon.Cash -> if (dark) Res.drawable._160_1 else Res.drawable._160_0
        AitaTabIcon.Card -> if (dark) Res.drawable._161_1 else Res.drawable._161_0
        AitaTabIcon.Mixed -> if (dark) Res.drawable._162_1 else Res.drawable._162_0
        AitaTabIcon.Name -> if (dark) Res.drawable._163_1 else Res.drawable._163_0
        AitaTabIcon.Price -> if (dark) Res.drawable._164_1 else Res.drawable._164_0
        AitaTabIcon.Quantity -> if (dark) Res.drawable._165_1 else Res.drawable._165_0
        AitaTabIcon.Ascending -> if (dark) Res.drawable._166_1 else Res.drawable._166_0
        AitaTabIcon.Descending -> if (dark) Res.drawable._167_1 else Res.drawable._167_0
        AitaTabIcon.Calendar -> if (dark) Res.drawable._168_1 else Res.drawable._168_0
        AitaTabIcon.Clock -> if (dark) Res.drawable._169_1 else Res.drawable._169_0
        AitaTabIcon.Person -> if (dark) Res.drawable._170_1 else Res.drawable._170_0
        AitaTabIcon.Work -> if (dark) Res.drawable._171_1 else Res.drawable._171_0
        AitaTabIcon.Workers -> if (dark) Res.drawable._172_1 else Res.drawable._172_0
        AitaTabIcon.Invite -> if (dark) Res.drawable._173_1 else Res.drawable._173_0
        AitaTabIcon.Requests -> if (dark) Res.drawable._174_1 else Res.drawable._174_0
        AitaTabIcon.Responses -> if (dark) Res.drawable._175_1 else Res.drawable._175_0
        AitaTabIcon.Remove -> if (dark) Res.drawable._176_1 else Res.drawable._176_0
        AitaTabIcon.Roles -> if (dark) Res.drawable._177_1 else Res.drawable._177_0
        AitaTabIcon.Store -> if (dark) Res.drawable._178_1 else Res.drawable._178_0
        AitaTabIcon.Branches -> if (dark) Res.drawable._179_1 else Res.drawable._179_0
        AitaTabIcon.Contract -> if (dark) Res.drawable._180_1 else Res.drawable._180_0
        AitaTabIcon.Payment -> if (dark) Res.drawable._181_1 else Res.drawable._181_0
        AitaTabIcon.Receipt -> if (dark) Res.drawable._182_1 else Res.drawable._182_0
        AitaTabIcon.Wallet -> if (dark) Res.drawable._183_1 else Res.drawable._183_0
        AitaTabIcon.Topup -> if (dark) Res.drawable._184_1 else Res.drawable._184_0
        AitaTabIcon.Orders -> if (dark) Res.drawable._185_1 else Res.drawable._185_0
        AitaTabIcon.Truck -> if (dark) Res.drawable._186_1 else Res.drawable._186_0
        AitaTabIcon.Settings -> if (dark) Res.drawable._187_1 else Res.drawable._187_0
        AitaTabIcon.Security -> if (dark) Res.drawable._188_1 else Res.drawable._188_0
        AitaTabIcon.Phone -> if (dark) Res.drawable._189_1 else Res.drawable._189_0
        AitaTabIcon.Email -> if (dark) Res.drawable._190_1 else Res.drawable._190_0
        AitaTabIcon.Password -> if (dark) Res.drawable._191_1 else Res.drawable._191_0
        AitaTabIcon.Chat -> if (dark) Res.drawable._192_1 else Res.drawable._192_0
        AitaTabIcon.Help -> if (dark) Res.drawable._193_1 else Res.drawable._193_0
        AitaTabIcon.Metrics -> if (dark) Res.drawable._194_1 else Res.drawable._194_0
        AitaTabIcon.Checklist -> if (dark) Res.drawable._195_1 else Res.drawable._195_0
        AitaTabIcon.Search -> if (dark) Res.drawable._196_1 else Res.drawable._196_0
        AitaTabIcon.Edit -> if (dark) Res.drawable._197_1 else Res.drawable._197_0
        AitaTabIcon.Check -> if (dark) Res.drawable._198_1 else Res.drawable._198_0
        AitaTabIcon.Cancel -> if (dark) Res.drawable._199_1 else Res.drawable._199_0
        AitaTabIcon.Warning -> if (dark) Res.drawable._200_1 else Res.drawable._200_0
        AitaTabIcon.Inbox -> if (dark) Res.drawable._201_1 else Res.drawable._201_0
        AitaTabIcon.Factory -> if (dark) Res.drawable._202_1 else Res.drawable._202_0
        AitaTabIcon.Plan -> if (dark) Res.drawable._203_1 else Res.drawable._203_0
        AitaTabIcon.Recover -> if (dark) Res.drawable._204_1 else Res.drawable._204_0
        AitaTabIcon.Compare -> if (dark) Res.drawable._205_1 else Res.drawable._205_0
        AitaTabIcon.Basket -> if (dark) Res.drawable._206_1 else Res.drawable._206_0
        AitaTabIcon.Info -> if (dark) Res.drawable._207_1 else Res.drawable._207_0
        AitaTabIcon.Barcode -> if (dark) Res.drawable._208_1 else Res.drawable._208_0
        AitaTabIcon.CashRegister -> if (dark) Res.drawable._209_1 else Res.drawable._209_0
    }
}
