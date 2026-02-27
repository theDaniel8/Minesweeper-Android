package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlin.random.Random

// -- ЛОКАЛИЗАЦИЯ --

enum class Language { RU, CS }

data class GameStrings(
    val difficultyTitle: String,
    val minesLabel: String,
    val wonStatus: String,
    val lostStatus: String,
    val playingStatus: String,
    val restartBtn: String,
    val easyName: String,
    val mediumName: String,
    val hardName: String,
    val langBtn: String
)

val russianStrings = GameStrings(
    difficultyTitle = "Сложность: ",
    minesLabel = "Мины: ",
    wonStatus = "ПОБЕДА! 🎉",
    lostStatus = "ПРОИГРЫШ 💣",
    playingStatus = "В игре",
    restartBtn = "Играть снова",
    easyName = "ЛЕГКО",
    mediumName = "СРЕДНЕ",
    hardName = "СЛОЖНО",
    langBtn = "RU"
)

val czechStrings = GameStrings(
    difficultyTitle = "Obtížnost: ",
    minesLabel = "Miny: ",
    wonStatus = "VÍTĚZSTVÍ! 🎉",
    lostStatus = "PROHRA 💣",
    playingStatus = "Ve hře",
    restartBtn = "Hrát znovu",
    easyName = "LEHKÁ",
    mediumName = "STŘEDNÍ",
    hardName = "TĚŽKÁ",
    langBtn = "CS"
)

// -- МОДЕЛИ ДАННЫХ --

enum class Difficulty(val rows: Int, val cols: Int, val mines: Int) {
    EASY(8, 8, 10),
    MEDIUM(12, 12, 30),
    HARD(16, 16, 60);

    fun getDisplayName(strings: GameStrings): String = when(this) {
        EASY -> strings.easyName
        MEDIUM -> strings.mediumName
        HARD -> strings.hardName
    }
}

data class Cell(
    val isMine: Boolean = false,
    val isRevealed: Boolean = false,
    val isFlagged: Boolean = false,
    val adjacentMines: Int = 0
)

enum class GameState {
    PLAYING, WON, LOST
}

// -- ГЛАВНЫЙ КОМПОНЕНТ --

@Composable
fun MinesweeperGame() {
    var language by remember { mutableStateOf(Language.RU) }
    val strings = if (language == Language.RU) russianStrings else czechStrings

    var difficulty by remember { mutableStateOf(Difficulty.EASY) }
    var gameKey by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { language = if (language == Language.RU) Language.CS else Language.RU },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(strings.langBtn)
            }

            DifficultySelector(
                selectedDifficulty = difficulty,
                onDifficultySelected = {
                    difficulty = it
                    gameKey++ 
                },
                strings = strings
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        key(gameKey) {
            GameBoard(difficulty = difficulty, strings = strings)
        }
    }
}

@Composable
fun DifficultySelector(
    selectedDifficulty: Difficulty,
    onDifficultySelected: (Difficulty) -> Unit,
    strings: GameStrings
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(strings.difficultyTitle, style = MaterialTheme.typography.bodySmall)
        Difficulty.entries.forEach { diff ->
            Button(
                onClick = { onDifficultySelected(diff) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (diff == selectedDifficulty) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.padding(horizontal = 2.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(diff.getDisplayName(strings), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun GameBoard(difficulty: Difficulty, strings: GameStrings) {
    val rows = difficulty.rows
    val cols = difficulty.cols
    val mines = difficulty.mines

    // Изначально создаем пустое поле
    var board by remember { mutableStateOf(List(rows) { List(cols) { Cell() } }) }
    var gameState by remember { mutableStateOf(GameState.PLAYING) }
    var isFlagMode by remember { mutableStateOf(false) }
    var isFirstMove by remember { mutableStateOf(true) }

    val flagsPlaced = board.flatten().count { it.isFlagged }
    val minesRemaining = mines - flagsPlaced

    fun revealEmptyCells(r: Int, c: Int, currentBoard: MutableList<MutableList<Cell>>) {
        if (r !in 0 until rows || c !in 0 until cols || 
            currentBoard[r][c].isRevealed || currentBoard[r][c].isFlagged) return

        currentBoard[r][c] = currentBoard[r][c].copy(isRevealed = true)

        if (currentBoard[r][c].adjacentMines == 0 && !currentBoard[r][c].isMine) {
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr != 0 || dc != 0) revealEmptyCells(r + dr, c + dc, currentBoard)
                }
            }
        }
    }

    fun checkWin(currentBoard: List<List<Cell>>) {
        val allSafeOpened = currentBoard.flatten().all { it.isMine || it.isRevealed }
        if (allSafeOpened) gameState = GameState.WON
    }

    fun toggleFlag(r: Int, c: Int) {
        if (gameState != GameState.PLAYING || board[r][c].isRevealed) return
        val newBoard = board.map { it.toMutableList() }.toMutableList()
        newBoard[r][c] = newBoard[r][c].copy(isFlagged = !newBoard[r][c].isFlagged)
        board = newBoard.map { it.toList() }
    }

    fun onCellClick(r: Int, c: Int) {
        if (gameState != GameState.PLAYING || board[r][c].isRevealed) return
        if (isFlagMode) { toggleFlag(r, c); return }
        if (board[r][c].isFlagged) return

        var activeBoard = board
        if (isFirstMove) {
            activeBoard = createBoard(rows, cols, mines, r, c)
            isFirstMove = false
        }

        val newBoard = activeBoard.map { it.toMutableList() }.toMutableList()
        if (newBoard[r][c].isMine) {
            gameState = GameState.LOST
            board = newBoard.map { row ->
                row.map { if (it.isMine) it.copy(isRevealed = true) else it }
            }
        } else {
            revealEmptyCells(r, c, newBoard)
            val updatedBoard = newBoard.map { it.toList() }
            board = updatedBoard
            checkWin(updatedBoard)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${strings.minesLabel} $minesRemaining",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = when (gameState) {
                    GameState.WON -> strings.wonStatus
                    GameState.LOST -> strings.lostStatus
                    GameState.PLAYING -> strings.playingStatus
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (gameState == GameState.LOST) Color.Red else if (gameState == GameState.WON) Color(0xFF388E3C) else Color.Unspecified
            )

            IconButton(
                onClick = { isFlagMode = !isFlagMode },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isFlagMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(if (isFlagMode) "🚩" else "⛏️", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
                .border(2.dp, Color.DarkGray)
        ) {
            Column {
                board.forEachIndexed { r, rowCells ->
                    Row {
                        rowCells.forEachIndexed { c, cell ->
                            CellView(
                                cell = cell,
                                onClick = { onCellClick(r, c) },
                                onLongClick = { toggleFlag(r, c) }
                            )
                        }
                    }
                }
            }
        }

        if (gameState != GameState.PLAYING) {
            Button(
                onClick = { 
                    board = List(rows) { List(cols) { Cell() } }
                    gameState = GameState.PLAYING
                    isFirstMove = true
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(strings.restartBtn)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CellView(cell: Cell, onClick: () -> Unit, onLongClick: () -> Unit) {
    val boxSize = 36.dp
    Box(
        modifier = Modifier
            .size(boxSize)
            .background(
                if (cell.isRevealed) {
                    if (cell.isMine) Color.Red else MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
            .border(0.5.dp, Color.Gray)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (cell.isRevealed) {
            if (cell.isMine) {
                Text("💣", fontSize = 18.sp)
            } else if (cell.adjacentMines > 0) {
                Text(
                    text = cell.adjacentMines.toString(),
                    fontWeight = FontWeight.Bold,
                    color = getNumberColor(cell.adjacentMines),
                    fontSize = 16.sp
                )
            }
        } else if (cell.isFlagged) {
            Text("🚩", fontSize = 18.sp)
        }
    }
}

private fun getNumberColor(number: Int): Color = when (number) {
    1 -> Color(0xFF1976D2)
    2 -> Color(0xFF388E3C)
    3 -> Color(0xFFD32F2F)
    4 -> Color(0xFF7B1FA2)
    else -> Color.Black
}

/**
 * Создает поле, гарантируя, что вокруг первого клика (excludeR, excludeC) нет мин.
 */
private fun createBoard(rows: Int, cols: Int, mines: Int, excludeR: Int, excludeC: Int): List<List<Cell>> {
    val cells = MutableList(rows) { MutableList(cols) { Cell() } }
    var placed = 0
    
    // Безопасная зона 3x3 вокруг первого клика
    val safeR = (excludeR - 1)..(excludeR + 1)
    val safeC = (excludeC - 1)..(excludeC + 1)

    while (placed < mines) {
        val r = Random.nextInt(rows)
        val c = Random.nextInt(cols)
        
        if (!cells[r][c].isMine && !(r in safeR && c in safeC)) {
            cells[r][c] = cells[r][c].copy(isMine = true)
            placed++
        }
    }
    
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (!cells[r][c].isMine) {
                var count = 0
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols && cells[nr][nc].isMine) count++
                    }
                }
                cells[r][c] = cells[r][c].copy(adjacentMines = count)
            }
        }
    }
    return cells.map { it.toList() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), 
                    color = MaterialTheme.colorScheme.background
                ) {
                    MinesweeperGame()
                }
            }
        }
    }
}
