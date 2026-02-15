package com.mobile.scrcpy.android.feature.remote.presentation

import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutBounds
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutLabelSource
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutNode
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutNodeKind
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutSnapshot
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal object RemoteUiLayoutParser {
    fun parse(xml: String): List<RemoteUiLayoutNode> {
        return parseSnapshot(xml).nodes
    }

    fun parseSnapshot(xml: String): RemoteUiLayoutSnapshot {
        if (xml.isBlank()) {
            return RemoteUiLayoutSnapshot(
                viewportBounds = RemoteUiLayoutBounds(0, 0, 1, 1),
                nodes = emptyList(),
            )
        }

        val builderFactory = DocumentBuilderFactory.newInstance().apply { configureSecureParsing() }
        val document =
            builderFactory
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml.trim())))

        val rootNodeElement = findRootNodeElement(document.documentElement)
        val nodes = mutableListOf<RemoteUiLayoutNode>()
        traverse(document.documentElement, nodes, inheritedComponentKey = null)

        val sortedNodes = postProcess(nodes)
        val viewportBounds =
            rootNodeElement
                ?.getAttribute("bounds")
                ?.let(::parseBounds)
                ?: inferViewportBounds(sortedNodes)

        return RemoteUiLayoutSnapshot(
            viewportBounds = viewportBounds,
            nodes = sortedNodes,
        )
    }

    private fun DocumentBuilderFactory.configureSecureParsing() {
        isNamespaceAware = false
        isIgnoringComments = true
        isCoalescing = true
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }

    private fun traverse(
        element: Element?,
        out: MutableList<RemoteUiLayoutNode>,
        inheritedComponentKey: String?,
    ) {
        if (element == null) {
            return
        }

        val childNodeElements =
            element
                .childElements()
                .filter { it.tagName == "node" }

        if (element.tagName == "node") {
            val currentComponentKey = deriveComponentKey(element, inheritedComponentKey)
            parseNode(
                element = element,
                isLeaf = childNodeElements.isEmpty(),
                componentKey = currentComponentKey,
            )?.takeIf(::shouldRender)?.let(out::add)

            childNodeElements.forEach { child ->
                traverse(child, out, inheritedComponentKey = currentComponentKey)
            }
            return
        }

        childNodeElements.forEach { child ->
            traverse(child, out, inheritedComponentKey = inheritedComponentKey)
        }
    }

    private fun findRootNodeElement(element: Element?): Element? {
        if (element == null) {
            return null
        }
        if (element.tagName == "node") {
            return element
        }
        return element.childElements().firstNotNullOfOrNull(::findRootNodeElement)
    }

    private fun parseNode(
        element: Element,
        isLeaf: Boolean,
        componentKey: String?,
    ): RemoteUiLayoutNode? {
        val bounds = parseBounds(element.getAttribute("bounds")) ?: return null
        val className = element.getAttribute("class").trim()
        val packageName = element.getAttribute("package").trim()
        val resourceId = element.getAttribute("resource-id").trim()
        val text = normalizeLabel(element.getAttribute("text"))
        val contentDescription = normalizeLabel(element.getAttribute("content-desc"))
        val password = element.getAttribute("password").toBoolean()
        val clickable = element.getAttribute("clickable").toBoolean()
        val focusable = element.getAttribute("focusable").toBoolean()
        val checkable = element.getAttribute("checkable").toBoolean()
        val kind =
            classifyNode(
                className = className,
                resourceId = resourceId,
                bounds = bounds,
                clickable = clickable,
                focusable = focusable,
                checkable = checkable,
            )
        val descendantSummary = summarizeDescendants(element)
        val parsedLabel =
            buildLabel(
                kind = kind,
                text = text,
                contentDescription = contentDescription,
                resourceId = resourceId,
                password = password,
            )

        return RemoteUiLayoutNode(
            bounds = bounds,
            label = parsedLabel.text,
            labelSource = parsedLabel.source,
            componentKey = componentKey,
            kind = kind,
            packageName = packageName,
            className = className,
            resourceId = resourceId,
            text = text,
            contentDescription = contentDescription,
            isLeaf = isLeaf,
            clickable = clickable,
            focusable = focusable,
            focused = element.getAttribute("focused").toBoolean(),
            checkable = checkable,
            checked = element.getAttribute("checked").toBoolean(),
            scrollable = element.getAttribute("scrollable").toBoolean(),
            password = password,
            visibleToUser = element.getAttribute("visible-to-user").ifBlank { "true" }.toBoolean(),
            hasInputDescendant = descendantSummary.hasInput,
            hasTextDescendant = descendantSummary.hasText,
            hasButtonDescendant = descendantSummary.hasButton,
            hasCheckIndicatorDescendant = descendantSummary.hasCheckIndicator,
        )
    }

    private fun deriveComponentKey(
        element: Element,
        inheritedComponentKey: String?,
    ): String? {
        val clickable = element.getAttribute("clickable").toBoolean()
        val focusable = element.getAttribute("focusable").toBoolean()
        if (!clickable && !focusable) {
            return inheritedComponentKey
        }

        val packageName = element.getAttribute("package").trim()
        val resourceId = element.getAttribute("resource-id").trim()
        val className = element.getAttribute("class").trim()
        val bounds = element.getAttribute("bounds").trim()
        val identity =
            when {
                resourceId.isNotBlank() -> resourceId
                className.isNotBlank() -> className
                else -> "node"
            }
        return "$packageName|$identity|$bounds"
    }

    private fun shouldRender(node: RemoteUiLayoutNode): Boolean {
        if (!node.visibleToUser || !node.bounds.hasArea()) {
            return false
        }

        if (shouldIgnoreSystemNode(node)) {
            return false
        }

        val hasSemanticContent =
            node.label.isNotBlank() ||
                node.text.isNotBlank() ||
                node.contentDescription.isNotBlank()
        val isInterestingLeaf =
            node.isLeaf &&
                node.kind in
                setOf(
                    RemoteUiLayoutNodeKind.TEXT,
                    RemoteUiLayoutNodeKind.BUTTON,
                    RemoteUiLayoutNodeKind.TOGGLE,
                )
        val isUsefulImageLeaf =
            node.isLeaf &&
                node.kind == RemoteUiLayoutNodeKind.IMAGE &&
                node.bounds.width in 16..160 &&
                node.bounds.height in 16..160
        val isInteractiveLeaf =
            node.isLeaf &&
                (node.clickable || node.focusable || node.checkable || node.scrollable)
        val isSemanticContainer =
            node.kind == RemoteUiLayoutNodeKind.CONTAINER &&
                node.hasInputDescendant &&
                (node.hasTextDescendant || node.hasButtonDescendant)
        val isLabeledInteractiveContainer =
            node.kind == RemoteUiLayoutNodeKind.CONTAINER &&
                node.label.isNotBlank() &&
                (node.clickable || node.focusable)
        val isCustomCheckContainer =
            node.kind == RemoteUiLayoutNodeKind.CONTAINER &&
                node.clickable &&
                looksLikeCheckableContainer(node)

        return when {
            node.kind == RemoteUiLayoutNodeKind.INPUT -> true
            isSemanticContainer -> true
            isLabeledInteractiveContainer -> true
            isCustomCheckContainer -> true
            hasSemanticContent && node.kind != RemoteUiLayoutNodeKind.CONTAINER -> true
            isInterestingLeaf -> true
            isUsefulImageLeaf -> true
            isInteractiveLeaf -> true
            else -> false
        }
    }

    private fun shouldIgnoreSystemNode(node: RemoteUiLayoutNode): Boolean {
        val resourceName =
            node.resourceId
                .substringAfterLast('/')
                .substringAfterLast(':')
                .trim()
                .lowercase()

        if (resourceName in IGNORED_RESOURCE_NAMES) {
            return true
        }

        if (shouldIgnoreDecorativeIndicatorNode(node, resourceName)) {
            return true
        }

        val shortClassName = node.className.substringAfterLast('.').lowercase()
        if (shortClassName in IGNORED_CLASS_NAMES) {
            return true
        }

        return false
    }

    private fun shouldIgnoreDecorativeIndicatorNode(
        node: RemoteUiLayoutNode,
        resourceName: String,
    ): Boolean {
        if (node.kind != RemoteUiLayoutNodeKind.IMAGE) {
            return false
        }
        if (node.text.isNotBlank() || node.contentDescription.isNotBlank()) {
            return false
        }
        if (!node.isLeaf) {
            return false
        }

        val looksLikeCheckIndicator =
            resourceName.contains("confirm") ||
                resourceName.contains("check") ||
                resourceName.contains("tick")

        val isSmallIndicator = node.bounds.width <= 80 && node.bounds.height <= 80
        return looksLikeCheckIndicator && isSmallIndicator
    }

    private fun looksLikeCheckableContainer(node: RemoteUiLayoutNode): Boolean {
        val lowered = node.resourceId.lowercase()
        return lowered.contains("check") ||
            lowered.contains("checkbox") ||
            lowered.contains("confirm") ||
            lowered.contains("agree") ||
            lowered.contains("accept") ||
            lowered.contains("consent") ||
            lowered.contains("policy") ||
            node.hasCheckIndicatorDescendant
    }

    private fun classifyNode(
        className: String,
        resourceId: String,
        bounds: RemoteUiLayoutBounds,
        clickable: Boolean,
        focusable: Boolean,
        checkable: Boolean,
    ): RemoteUiLayoutNodeKind {
        val shortName = className.substringAfterLast('.')
        return when {
            shortName.contains("EditText", ignoreCase = true) ||
                shortName.contains("TextInput", ignoreCase = true) ||
                shortName.contains("AutoComplete", ignoreCase = true) -> RemoteUiLayoutNodeKind.INPUT

            shortName.contains("CompoundButton", ignoreCase = true) ||
                shortName.contains("CheckBox", ignoreCase = true) ||
                shortName.contains("Switch", ignoreCase = true) ||
                shortName.contains("Toggle", ignoreCase = true) ||
                shortName.contains("RadioButton", ignoreCase = true) -> RemoteUiLayoutNodeKind.TOGGLE

            shortName.contains("Button", ignoreCase = true) ||
                shortName.contains("Chip", ignoreCase = true) -> RemoteUiLayoutNodeKind.BUTTON

            shortName.contains("Image", ignoreCase = true) -> RemoteUiLayoutNodeKind.IMAGE

            shortName.contains("TextView", ignoreCase = true) ||
                shortName.contains("Text", ignoreCase = true) -> RemoteUiLayoutNodeKind.TEXT

            looksLikeCustomToggleView(
                className = className,
                resourceId = resourceId,
                bounds = bounds,
                clickable = clickable,
                focusable = focusable,
                checkable = checkable,
            ) -> RemoteUiLayoutNodeKind.TOGGLE

            shortName.contains("Layout", ignoreCase = true) ||
                shortName.contains("ViewGroup", ignoreCase = true) ||
                shortName.contains("RecyclerView", ignoreCase = true) ||
                shortName.contains("ListView", ignoreCase = true) ||
                shortName.contains("ScrollView", ignoreCase = true) ||
                shortName.contains("WebView", ignoreCase = true) -> RemoteUiLayoutNodeKind.CONTAINER

            else -> RemoteUiLayoutNodeKind.OTHER
        }
    }

    private fun looksLikeCustomToggleView(
        className: String,
        resourceId: String,
        bounds: RemoteUiLayoutBounds,
        clickable: Boolean,
        focusable: Boolean,
        checkable: Boolean,
    ): Boolean {
        if (checkable) {
            return false
        }

        if (!clickable && !focusable) {
            return false
        }

        val shortName = className.substringAfterLast('.')
        if (!shortName.equals("View", ignoreCase = true)) {
            return false
        }

        val loweredResourceId = resourceId.lowercase()
        val matchesToggleResource =
            loweredResourceId.contains(":id/scb_") ||
                loweredResourceId.contains("/scb_") ||
                loweredResourceId.contains("check") ||
                loweredResourceId.contains("toggle") ||
                loweredResourceId.contains("switch") ||
                loweredResourceId.contains("radio")
        if (!matchesToggleResource) {
            return false
        }

        val width = bounds.width
        val height = bounds.height
        if (width !in 16..96 || height !in 16..96) {
            return false
        }

        val larger = maxOf(width, height).toFloat()
        val smaller = minOf(width, height).coerceAtLeast(1).toFloat()
        return larger / smaller <= 1.8f
    }

    private fun buildLabel(
        kind: RemoteUiLayoutNodeKind,
        text: String,
        contentDescription: String,
        resourceId: String,
        password: Boolean,
    ): ParsedLabel {
        val sanitizedText = sanitizePrimaryLabel(text, kind)
        if (sanitizedText.isNotBlank()) {
            return ParsedLabel(sanitizedText.take(48), RemoteUiLayoutLabelSource.TEXT)
        }

        val sanitizedContentDescription = sanitizePrimaryLabel(contentDescription, kind)
        if (sanitizedContentDescription.isNotBlank()) {
            return ParsedLabel(sanitizedContentDescription.take(48), RemoteUiLayoutLabelSource.CONTENT_DESCRIPTION)
        }

        val resourceName =
            resourceId
                .substringAfterLast('/')
                .substringAfterLast(':')
                .trim()

        if (resourceName.isNotBlank() && shouldUseResourceFallback(kind)) {
            if (isArrowLikeResourceName(resourceName) && kind in setOf(RemoteUiLayoutNodeKind.IMAGE, RemoteUiLayoutNodeKind.OTHER, RemoteUiLayoutNodeKind.BUTTON)) {
                return ParsedLabel("", RemoteUiLayoutLabelSource.FALLBACK)
            }
            val humanized = humanizeResourceName(resourceName)
            if (isMeaningfulFallbackLabel(humanized)) {
                return ParsedLabel(humanized.take(48), RemoteUiLayoutLabelSource.RESOURCE_ID)
            }
        }

        if (password) {
            return ParsedLabel("Password", RemoteUiLayoutLabelSource.FALLBACK)
        }

        return ParsedLabel("", RemoteUiLayoutLabelSource.FALLBACK)
    }

    private fun sanitizePrimaryLabel(
        rawLabel: String,
        kind: RemoteUiLayoutNodeKind,
    ): String {
        val label = rawLabel.trim()
        if (label.isBlank()) {
            return ""
        }

        if (kind == RemoteUiLayoutNodeKind.BUTTON && isKeyboardLikeButtonLabel(label)) {
            return label
        }

        val lowered = label.lowercase()
        if (lowered in GENERIC_LABELS || (label in SYMBOL_ONLY_LABELS && kind !in setOf(RemoteUiLayoutNodeKind.TEXT, RemoteUiLayoutNodeKind.INPUT, RemoteUiLayoutNodeKind.BUTTON))) {
            return ""
        }

        if (kind == RemoteUiLayoutNodeKind.TOGGLE || kind == RemoteUiLayoutNodeKind.IMAGE || kind == RemoteUiLayoutNodeKind.OTHER) {
            if (label.length <= 1 || isSuspiciousShortCodeLabel(label)) {
                return ""
            }
        }

        return label
    }

    private fun shouldUseResourceFallback(kind: RemoteUiLayoutNodeKind): Boolean =
        kind == RemoteUiLayoutNodeKind.INPUT ||
            kind == RemoteUiLayoutNodeKind.TEXT ||
            kind == RemoteUiLayoutNodeKind.BUTTON ||
            kind == RemoteUiLayoutNodeKind.IMAGE

    private fun isMeaningfulFallbackLabel(label: String): Boolean {
        val lowered = label.trim().lowercase()
        return lowered.isNotBlank() &&
            lowered !in GENERIC_LABELS &&
            !isSuspiciousShortCodeLabel(label)
    }

    private fun humanizeResourceName(resourceName: String): String {
        val normalized =
            resourceName
                .replace(CAMEL_CASE_REGEX, "$1 $2")
                .replace(RESOURCE_SEPARATOR_REGEX, " ")
                .trim()

        val tokens =
            normalized
                .split(' ')
                .map { it.trim() }
                .filter { it.isNotBlank() }

        val meaningfulTokens =
            tokens.filterNot { token -> token.lowercase() in COMMON_RESOURCE_GENERIC_TOKENS }

        if (tokens.isNotEmpty() && meaningfulTokens.isEmpty()) {
            return ""
        }

        return meaningfulTokens
            .ifEmpty { tokens }
            .joinToString(" ") { token ->
                when (token.lowercase()) {
                    "qq" -> "QQ"
                    "wx" -> "WX"
                    "wechat" -> "WeChat"
                    "alipay" -> "Alipay"
                    "tb" -> "TB"
                    "onekey" -> "OneKey"
                    "ctid" -> "CTID"
                    else -> token.replaceFirstChar { char -> char.uppercase() }
                }
            }.ifBlank { resourceName }
    }

    private fun isArrowLikeResourceName(resourceName: String): Boolean {
        val lowered = resourceName.lowercase()
        return lowered.contains("arrow") ||
            lowered.contains("dropdown") ||
            lowered.contains("expand") ||
            lowered.contains("chevron") ||
            lowered.contains("caret") ||
            lowered.contains("spinner") ||
            lowered.contains("region_image") ||
            lowered.contains("regionimage")
    }

    private fun parseBounds(rawBounds: String): RemoteUiLayoutBounds? {
        val values =
            BOUNDS_REGEX
                .find(rawBounds.trim())
                ?.groupValues
                ?.drop(1)
                ?.mapNotNull { it.toIntOrNull() }
                ?: return null

        if (values.size != 4) {
            return null
        }

        return RemoteUiLayoutBounds(
            left = values[0],
            top = values[1],
            right = values[2],
            bottom = values[3],
        )
    }

    private fun postProcess(nodes: List<RemoteUiLayoutNode>): List<RemoteUiLayoutNode> {
        val sorted =
            nodes.sortedWith(
                compareByDescending<RemoteUiLayoutNode> { nodePriority(it) }
                    .thenByDescending { it.bounds.area() },
            )

        val kept = mutableListOf<RemoteUiLayoutNode>()
        sorted.forEach { candidate ->
            if (kept.none { existing -> isRedundant(candidate, existing) }) {
                kept += candidate
            }
        }
        return kept.sortedByDescending { it.bounds.area() }
    }

    private fun isRedundant(
        candidate: RemoteUiLayoutNode,
        existing: RemoteUiLayoutNode,
    ): Boolean {
        val overlapRatio = overlapOverSmallerArea(candidate.bounds, existing.bounds)
        if (overlapRatio < 0.88f) {
            return false
        }

        val candidateNormalized = normalizeForComparison(candidate.label)
        val existingNormalized = normalizeForComparison(existing.label)
        if (candidateNormalized.isNotBlank() && candidateNormalized == existingNormalized) {
            return true
        }

        if (existing.kind == RemoteUiLayoutNodeKind.CONTAINER &&
            candidate.kind == RemoteUiLayoutNodeKind.CONTAINER &&
            existing.label.isBlank() &&
            candidate.label.isBlank() &&
            candidate.bounds.area() <= existing.bounds.area()
        ) {
            return true
        }

        if (existing.labelSource == RemoteUiLayoutLabelSource.TEXT && candidate.labelSource != RemoteUiLayoutLabelSource.TEXT) {
            return true
        }

        if (existing.kind in setOf(RemoteUiLayoutNodeKind.TEXT, RemoteUiLayoutNodeKind.INPUT) &&
            candidate.kind in setOf(RemoteUiLayoutNodeKind.BUTTON, RemoteUiLayoutNodeKind.IMAGE, RemoteUiLayoutNodeKind.OTHER, RemoteUiLayoutNodeKind.TOGGLE)
        ) {
            return true
        }

        return false
    }

    private fun overlapOverSmallerArea(
        a: RemoteUiLayoutBounds,
        b: RemoteUiLayoutBounds,
    ): Float {
        val intersectionLeft = maxOf(a.left, b.left)
        val intersectionTop = maxOf(a.top, b.top)
        val intersectionRight = minOf(a.right, b.right)
        val intersectionBottom = minOf(a.bottom, b.bottom)
        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0f
        }

        val intersectionArea = (intersectionRight - intersectionLeft).toLong() * (intersectionBottom - intersectionTop).toLong()
        val smallerArea = minOf(a.area(), b.area()).coerceAtLeast(1L)
        return intersectionArea.toFloat() / smallerArea.toFloat()
    }

    private fun normalizeForComparison(label: String): String = label.lowercase().replace(WHITESPACE_REGEX, "").trim()

    private fun nodePriority(node: RemoteUiLayoutNode): Int {
        val sourceScore =
            when (node.labelSource) {
                RemoteUiLayoutLabelSource.TEXT -> 40
                RemoteUiLayoutLabelSource.CONTENT_DESCRIPTION -> 30
                RemoteUiLayoutLabelSource.RESOURCE_ID -> 20
                RemoteUiLayoutLabelSource.FALLBACK -> 10
            }
        val kindScore =
            when (node.kind) {
                RemoteUiLayoutNodeKind.INPUT -> 8
                RemoteUiLayoutNodeKind.TEXT -> 7
                RemoteUiLayoutNodeKind.BUTTON -> 5
                RemoteUiLayoutNodeKind.TOGGLE -> 4
                RemoteUiLayoutNodeKind.IMAGE -> 3
                RemoteUiLayoutNodeKind.OTHER -> 2
                RemoteUiLayoutNodeKind.CONTAINER -> 1
            }
        return sourceScore + kindScore
    }

    private fun inferViewportBounds(nodes: List<RemoteUiLayoutNode>): RemoteUiLayoutBounds {
        if (nodes.isEmpty()) {
            return RemoteUiLayoutBounds(0, 0, 1, 1)
        }
        return RemoteUiLayoutBounds(
            left = nodes.minOf { it.bounds.left },
            top = nodes.minOf { it.bounds.top },
            right = nodes.maxOf { it.bounds.right },
            bottom = nodes.maxOf { it.bounds.bottom },
        )
    }

    private fun normalizeLabel(rawValue: String): String = rawValue.replace(WHITESPACE_REGEX, " ").trim()

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodeList = childNodes
        for (index in 0 until nodeList.length) {
            val child = nodeList.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) {
                result += child as Element
            }
        }
        return result
    }

    private val BOUNDS_REGEX = Regex("""\[(\-?\d+),(\-?\d+)]\[(\-?\d+),(\-?\d+)]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val CAMEL_CASE_REGEX = Regex("([a-z])([A-Z])")
    private val RESOURCE_SEPARATOR_REGEX = Regex("""[_\-.]+""")
    private val COMMON_RESOURCE_GENERIC_TOKENS =
        setOf(
            "btn",
            "iv",
            "img",
            "icon",
            "layout",
            "view",
            "container",
            "item",
            "tv",
            "et",
            "ll",
            "rl",
            "fl",
            "ali",
            "user",
            "guide",
            "image",
            "navigation",
            "bar",
            "label",
            "labels",
            "group",
            "small",
        )
    private val GENERIC_LABELS =
        setOf(
            "divider",
            "separator",
            "icon",
            "image",
            "background",
            "lottie",
            "animation",
            "button",
            "toggle",
            "checkbox",
            "radio",
            "switch",
            "view",
            "layout",
            "container",
            "prefix",
            "suffix",
            "start",
            "end",
            "left",
            "right",
        )
    private val SYMBOL_ONLY_LABELS = setOf("✓", "✔", "☑", "√", "v", "V")
    private val IGNORED_RESOURCE_NAMES =
        setOf(
            "navigationbarbackground",
            "statusbarbackground",
            "navigationbarframe",
            "navbarbackground",
        )
    private val IGNORED_CLASS_NAMES =
        setOf(
            "navigationbarbackground",
            "statusbarbackground",
        )

    private fun isSuspiciousShortCodeLabel(label: String): Boolean {
        if (label.any { it.code in 0x4E00..0x9FFF }) {
            return false
        }

        val tokens =
            label
                .lowercase()
                .split(' ')
                .map { it.trim() }
                .filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            return false
        }

        return when {
            tokens.size >= 2 -> tokens.all { it.length <= 4 && it.any(Char::isLetter) }
            tokens.size == 1 -> tokens.single().length <= 3 && tokens.single().any(Char::isLetter)
            else -> false
        }
    }

    private fun isKeyboardLikeButtonLabel(label: String): Boolean =
        label.length == 1 && (label.first().isDigit() || label in setOf(".", ",", "*", "#")) ||
            label in setOf("删除", "退格", "完成", "确定", "下一步", "ABC", "123")

    private fun summarizeDescendants(element: Element): DescendantSummary {
        var hasInput = false
        var hasText = false
        var hasButton = false
        var hasCheckIndicator = false

        val childNodeElements =
            element
                .childElements()
                .filter { it.tagName == "node" }

        childNodeElements.forEach { child ->
            val childBounds =
                parseBounds(child.getAttribute("bounds"))
                    ?: RemoteUiLayoutBounds(0, 0, 0, 0)
            val childKind =
                classifyNode(
                    className = child.getAttribute("class").trim(),
                    resourceId = child.getAttribute("resource-id").trim(),
                    bounds = childBounds,
                    clickable = child.getAttribute("clickable").toBoolean(),
                    focusable = child.getAttribute("focusable").toBoolean(),
                    checkable = child.getAttribute("checkable").toBoolean(),
                )
            when (childKind) {
                RemoteUiLayoutNodeKind.INPUT -> hasInput = true
                RemoteUiLayoutNodeKind.TEXT -> hasText = true
                RemoteUiLayoutNodeKind.BUTTON -> hasButton = true
                else -> Unit
            }

            val childResourceId = child.getAttribute("resource-id").trim().lowercase()
            if (
                childResourceId.contains("check") ||
                childResourceId.contains("checkbox") ||
                childResourceId.contains("confirm") ||
                childResourceId.contains("tick")
            ) {
                hasCheckIndicator = true
            }

            val childSummary = summarizeDescendants(child)
            hasInput = hasInput || childSummary.hasInput
            hasText = hasText || childSummary.hasText
            hasButton = hasButton || childSummary.hasButton
            hasCheckIndicator = hasCheckIndicator || childSummary.hasCheckIndicator
        }

        return DescendantSummary(
            hasInput = hasInput,
            hasText = hasText,
            hasButton = hasButton,
            hasCheckIndicator = hasCheckIndicator,
        )
    }

    private data class ParsedLabel(
        val text: String,
        val source: RemoteUiLayoutLabelSource,
    )

    private data class DescendantSummary(
        val hasInput: Boolean,
        val hasText: Boolean,
        val hasButton: Boolean,
        val hasCheckIndicator: Boolean,
    )
}
