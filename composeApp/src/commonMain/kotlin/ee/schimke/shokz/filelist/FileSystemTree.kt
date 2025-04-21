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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.net.URLDecoder

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
    val isDirectory = rememberIsDirectory(fileSystem, path)

    val displayName = remember (path) { URLDecoder.decode(path.toString(), Charsets.UTF_8) }
    val parts = remember (path) { displayName.split("/", ":").filter { it.isNotEmpty() } }

    if (isRoot || isDirectory.value == true) {
        Branch(
            content = path,
            name = parts.last(),
            customIcon = { if (isRoot) style.RootNodeIcon(it) else style.DefaultNodeIcon(it) },
            customName = { if (isRoot) style.RootNodeName(it) else style.DefaultNodeName(it) }
        ) {
            val children by rememberFileList(fileSystem, path)

            if (children != null) {
                children?.forEach { path ->
                    ShokzFileSystemNode(
                        style = style,
                        path,
                        fileSystem,
                        isRoot = false
                    )
                }
            } else {
                Leaf(
                    content = "Loading...",
                )
            }
        }
    } else {
        Leaf(
            content = path,
            name = parts.last(),
            customIcon = { style.DefaultNodeIcon(it) },
            customName = { style.DefaultNodeName(it) }
        )
    }
}

@Composable
private fun rememberIsDirectory(fileSystem: FileSystem, path: Path): State<Boolean?> {
    val result = remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            result.value = fileSystem.metadata(path).isDirectory
        }
    }

    return result
}

@Composable
private fun rememberFileList(fileSystem: FileSystem, path: Path): State<List<Path>?> {
    val result = remember { mutableStateOf<List<Path>?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            result.value = fileSystem.listOrNull(path)
        }
    }

    return result
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