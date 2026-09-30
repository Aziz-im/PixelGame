package game;

import javax.swing.*;
import java.awt.CardLayout;

public class GameWindow extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final GamePanel gamePanel = new GamePanel(this);

    public GameWindow() {
        setTitle("Моя игра");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        root.add(new MenuPanel(this), "MENU");
        root.add(gamePanel, "GAME");
        add(root);

        setResizable(false);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        showMenu();
    }

    public void showMenu() {
        gamePanel.stop();
        cards.show(root, "MENU");
    }

    public void startGame() {
        cards.show(root, "GAME");
        gamePanel.start();
        gamePanel.requestFocusInWindow();
    }
}