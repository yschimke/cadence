package ee.schimke.shokz.filelist

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.bonsai.core.BonsaiStyle
import cafe.adriel.bonsai.core.BonsaiStyle.Companion.DefaultNodeTextStyle
import cafe.adriel.bonsai.core.node.Branch
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.node.Leaf
import cafe.adriel.bonsai.core.node.Node
import cafe.adriel.bonsai.core.tree.Tree
import cafe.adriel.bonsai.core.tree.TreeScope
import okio.FileSystem
import okio.Path

@Composable
fun shokzFileSystemTree(
    style: BonsaiStyle<Path> = ShokzBonsaiStyle(),
    rootPath: Path,
    fileSystem: FileSystem,
    isRoot: Boolean = true,
): Tree<Path> =
    Tree {
        ShokzFileSystemNode(style, rootPath, fileSystem, isRoot = isRoot)
    }

@Composable
fun TreeScope.ShokzFileSystemNode(
    style: BonsaiStyle<Path>,
    path: Path,
    fileSystem: FileSystem,
    isRoot: Boolean,
) {
    if (fileSystem.metadata(path).isDirectory) {
        val name =
            if (isRoot) path.name.substringAfterLast("%3A")
                .replace("%2F", "/") else path.name.substringAfterLast("%2F")

        Branch(
            content = path,
            name = name,
            customIcon = { if (isRoot) style.RootNodeIcon(it) else style.DefaultNodeIcon(it) },
            customName = { if (isRoot) style.RootNodeName(it) else style.DefaultNodeName(it) }
        ) {
            fileSystem
                .listOrNull(path)
                ?.forEach { path ->
                    ShokzFileSystemNode(
                        style = style,
                        path,
                        fileSystem,
                        isRoot = false
                    )
                }
        }
    } else {
        Leaf(
            content = path,
            name = path.name.substringAfterLast("%2F"),
            customIcon = { style.DefaultNodeIcon(it) },
            customName = { style.DefaultNodeName(it) }
        )
    }
}

@Composable
fun <T> BonsaiStyle<T>.DefaultNodeIcon(node: Node<T>) {
    val (icon, colorFilter) = if (node is BranchNode && node.isExpanded) {
        this.nodeExpandedIcon(node) to this.nodeExpandedIconColorFilter
    } else {
        this.nodeCollapsedIcon(node) to this.nodeCollapsedIconColorFilter
    }

    if (icon != null) {
        Image(
            painter = icon,
            colorFilter = colorFilter,
            contentDescription = node.name,
        )
    }
}

@Composable
fun <T> BonsaiStyle<T>.RootNodeIcon(node: Node<T>) {
    Image(
        Icons.Default.DriveFolderUpload,
        contentDescription = node.name,
    )
}

@Composable
fun <T> BonsaiStyle<T>.DefaultNodeName(node: Node<T>) {
    BasicText(
        text = node.name,
        style = this.nodeNameTextStyle,
        modifier = Modifier.padding(start = this.nodeNameStartPadding)
    )
}

@Composable
fun <T> BonsaiStyle<T>.RootNodeName(node: Node<T>) {
    BasicText(
        text = node.name,
        style = this.nodeNameTextStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = this.nodeNameStartPadding)
    )
}

fun ShokzBonsaiStyle(): BonsaiStyle<Path> =
    BonsaiStyle(
        nodeNameStartPadding = 4.dp,
        nodeCollapsedIcon = { node ->
            rememberVectorPainter(
                if (node is BranchNode) Icons.Outlined.Folder
                else Icons.AutoMirrored.Outlined.InsertDriveFile
            )
        },
        nodeExpandedIcon = {
            rememberVectorPainter(Icons.Outlined.FolderOpen)
        },
        toggleIcon = { rememberVectorPainter(Icons.Default.ChevronRight) },
        nodeNameTextStyle = DefaultNodeTextStyle.copy(fontSize = 15.sp)
    )