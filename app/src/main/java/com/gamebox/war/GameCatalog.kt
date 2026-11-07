package com.gamebox.war

import androidx.annotation.StringRes

data class GameCatalogItem(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val effort: Int,
    val artwork: Int,
    val isAvailable: Boolean = false
)

data class GameCatalogCategory(
    @StringRes val title: Int,
    val games: List<GameCatalogItem>
)

object GameCatalog {
    val categories = listOf(
        GameCatalogCategory(
            R.string.category_card_games,
            listOf(
                GameCatalogItem(R.string.game_war, R.string.game_war_description, R.string.effort_easy, R.drawable.art_cards, true),
                GameCatalogItem(R.string.game_taki, R.string.game_taki_description, R.string.effort_medium, R.drawable.art_cards, true),
                GameCatalogItem(R.string.game_memory, R.string.game_memory_description, R.string.effort_easy, R.drawable.art_cards),
                GameCatalogItem(R.string.game_higher_lower, R.string.game_higher_lower_description, R.string.effort_easy, R.drawable.art_cards),
                GameCatalogItem(R.string.game_snap, R.string.game_snap_description, R.string.effort_easy, R.drawable.art_cards),
                GameCatalogItem(R.string.game_go_fish, R.string.game_go_fish_description, R.string.effort_medium, R.drawable.art_cards),                
                GameCatalogItem(R.string.game_solitaire, R.string.game_solitaire_description, R.string.effort_medium, R.drawable.art_cards)
            )
        ),
        GameCatalogCategory(
            R.string.category_board_games,
            listOf(
                GameCatalogItem(R.string.game_tic_tac_toe, R.string.game_tic_tac_toe_description, R.string.effort_easy, R.drawable.art_board),
                GameCatalogItem(R.string.game_connect_four, R.string.game_connect_four_description, R.string.effort_easy, R.drawable.art_board),
                GameCatalogItem(R.string.game_dots_boxes, R.string.game_dots_boxes_description, R.string.effort_easy, R.drawable.art_board),
                GameCatalogItem(R.string.game_checkers, R.string.game_checkers_description, R.string.effort_medium, R.drawable.art_board),
                GameCatalogItem(R.string.game_battleship, R.string.game_battleship_description, R.string.effort_medium, R.drawable.art_board)
            )
        ),
        GameCatalogCategory(
            R.string.category_word_number_games,
            listOf(
                GameCatalogItem(R.string.game_guess_number, R.string.game_guess_number_description, R.string.effort_easy, R.drawable.art_words_numbers),
                GameCatalogItem(R.string.game_hangman, R.string.game_hangman_description, R.string.effort_easy, R.drawable.art_words_numbers),
                GameCatalogItem(R.string.game_2048, R.string.game_2048_description, R.string.effort_easy, R.drawable.art_words_numbers),
                GameCatalogItem(R.string.game_sudoku, R.string.game_sudoku_description, R.string.effort_medium, R.drawable.art_words_numbers),
                GameCatalogItem(R.string.game_word_search, R.string.game_word_search_description, R.string.effort_medium, R.drawable.art_words_numbers),
                GameCatalogItem(R.string.game_guess_the_letter, R.string.game_guess_the_letter_description, R.string.effort_easy, R.drawable.art_words_numbers, true),
                GameCatalogItem(R.string.game_write_the_letter, R.string.game_write_the_letter_description, R.string.effort_easy, R.drawable.art_words_numbers, true)
            )
        ),
        GameCatalogCategory(
            R.string.category_strategy_games,
            listOf(
                GameCatalogItem(R.string.game_nim, R.string.game_nim_description, R.string.effort_easy, R.drawable.art_strategy),
                GameCatalogItem(R.string.game_reversi, R.string.game_reversi_description, R.string.effort_medium, R.drawable.art_strategy)
            )
        )
    )
}
