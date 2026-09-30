package game;

import javax.swing.*;
import java.awt.*;

public class MenuPanel extends JPanel {

    public MenuPanel(GameWindow window) {
        setPreferredSize(new Dimension(Room.COLS * Room.TILE, Room.ROWS * Room.TILE));
        setBackground(new Color(20, 20, 40));
        setLayout(new GridBagLayout());

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("МОЯ ИГРА");
        title.setFont(new Font("Monospaced", Font.BOLD, 56));
        title.setForeground(new Color(240, 200, 60));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton play = makeButton("ИГРАТЬ");
        play.addActionListener(e -> window.startGame());

        JButton exit = makeButton("ВЫХОД");
        exit.addActionListener(e -> System.exit(0));

        box.add(title);
        box.add(Box.createVerticalStrut(40));
        box.add(play);
        box.add(Box.createVerticalStrut(15));
        box.add(exit);
        add(box);
    }

    private JButton makeButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Monospaced", Font.BOLD, 28));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(60, 60, 110));
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(300, 60));
        b.setPreferredSize(new Dimension(300, 60));
        return b;
    }
}