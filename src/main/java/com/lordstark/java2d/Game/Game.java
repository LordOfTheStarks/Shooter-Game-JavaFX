package com.lordstark.java2d.Game;

import java.util.*;

import com.lordstark.java2d.AppConfig;
import com.lordstark.java2d.Menu.MainMenu;
import com.lordstark.java2d.Menu.Settings;
import javafx.animation.KeyFrame;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Game extends Application {

    // Game objects and some variables
    public static Camera camera;
    private static final double SPEED = 6; // Speed of the player, it is optimal
    private Player player;
    private final Map<KeyCode, Boolean> keys = new HashMap<>();
    public static List<Enemy> enemies = new ArrayList<>(); // List of enemies
    private int currentTentIndex = 0; // For tent locations
    private int score = 0; // Player starting score
    private TerrainManager terrainManager;

    private Stage primaryStage; // Main stage
    private Timeline gameLoop; // Game loop
    private StackPane root; // Root layout
    private Canvas canvas; // Canvas to draw the game
    private VBox gameOverMenu; // Game over menu layout
    private VBox pauseMenu; // Pause menu layout
    private boolean gameWon = false; // for Win condition
    private boolean isPaused = false; // for paused condition

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Shooter game"); // Title of the game window

        initializeGame(); // initialize the game's compononents
    }

    private void initializeGame() {
        // Reset game state
        score = 0;
        gameWon = false;
        isPaused = false;
        enemies.clear(); // Clear enemies
        Player.bullets.clear(); // Clear bullets

        root = new StackPane(); // Create root layout
        terrainManager = new TerrainManager();
        camera = new Camera(0, 0); // Init camera

        // Get width and height from AppConfig
        canvas = new Canvas(AppConfig.getWidth(), AppConfig.getHeight());
        canvas.setFocusTraversable(true);
        GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
        root.getChildren().add(canvas);

        this.player = new Player(40, -20); // Create the player at this position, manually arranged
        this.player.setTerrainManager(terrainManager);

        gameLoop = new Timeline(new KeyFrame(Duration.millis(1000.0/60),
                                             e -> update(graphicsContext)));
        gameLoop.setCycleCount(Animation.INDEFINITE); // Infinite loop
        gameLoop.play(); // Start the game loop

        currentTentIndex = 0; // Reset tent index
        spawnEnemies(); // Start enemy spawning

        for (Enemy e : enemies) {
            e.setTerrainManager(terrainManager);
        }

        setupInputHandlers(); // Set up key and mouse input handlers

        // Set up scene and show the stage
        Scene scene = new Scene(root, AppConfig.getWidth(), AppConfig.getHeight());
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initialize menus but it don't add them yet
        createGameOverMenu();
        createPauseMenu();
    }
    // Create the pause menu layout with buttons for various actions
    private void createPauseMenu() {
        pauseMenu = new VBox(20);
        pauseMenu.setAlignment(Pos.CENTER);
        pauseMenu.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8);");
        pauseMenu.setPrefWidth(AppConfig.getWidth());
        pauseMenu.setPrefHeight(AppConfig.getHeight());

        Text pauseText = new Text("Game Paused");
        pauseText.setFont(Font.loadFont(Objects.requireNonNull(
                MainMenu.class.getResource("/joystix-monospace.otf")).toExternalForm(), 40));
        pauseText.setFill(Color.WHITE);

        Button resumeButton = new Button("Resume");
        resumeButton.setOnAction(e -> resumeGame());
        styleButton(resumeButton);

        Button replayButton = new Button("Replay");
        replayButton.setOnAction(e -> restartGame());
        styleButton(replayButton);

        Button settingsButton = new Button("Settings");
        settingsButton.setOnAction(e -> openSettings());
        styleButton(settingsButton);

        Button mainMenuButton = new Button("Main Menu");
        mainMenuButton.setOnAction(e -> returnToMainMenu());
        styleButton(mainMenuButton);

        Button quitButton = new Button("Quit");
        quitButton.setOnAction(e -> System.exit(0));
        styleButton(quitButton);

        pauseMenu.getChildren().addAll(
                pauseText,
                resumeButton,
                replayButton,
                settingsButton,
                mainMenuButton,
                quitButton
        );
    }
    // Create the game over menu layout with options to replay or quit
    private void createGameOverMenu() {
        gameOverMenu = new VBox(20);
        gameOverMenu.setAlignment(Pos.CENTER);

        Button replayButton = new Button("Replay");
        replayButton.setOnAction(e -> restartGame());
        styleButton(replayButton);

        Button quitButton = new Button("Quit");
        quitButton.setOnAction(e -> System.exit(0));
        styleButton(quitButton);

        gameOverMenu.getChildren().addAll(replayButton, quitButton);
    }
    // Helper method to style buttons in menus
    private void styleButton(Button button) {
        button.setStyle(
                "-fx-background-color: #4CAF50;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-padding: 10px 20px;" +
                        "-fx-min-width: 200px;" +
                        "-fx-cursor: hand"
        );

        button.setOnMouseEntered(e ->
                                         button.setStyle(button.getStyle() + ";-fx-background-color: #45a049;"));
        button.setOnMouseExited(e ->
                                        button.setStyle(button.getStyle().replace(";-fx-background-color: #45a049", "")));
    }
    // Handle game over state and display the corresponding message
    private void handleGameOver() {
        gameLoop.stop();

        // Create the text depending on the situation
        Text gameOverText = new Text(gameWon ? "You Won" : "You Lost");
        gameOverText.setFont(Font.loadFont(Objects.requireNonNull(
                MainMenu.class.getResource("/joystix-monospace.otf")).toExternalForm(), 40));
        gameOverText.setFill(Color.WHITE);

        // Clears previous text if it exists
        gameOverMenu.getChildren().removeIf(node -> node instanceof Text);
        gameOverMenu.getChildren().add(0, gameOverText);

        root.getChildren().add(gameOverMenu);
    }

    // Set up input handlers for key and mouse actions
    private void setupInputHandlers() {
        canvas.setOnKeyPressed(e -> {
            keys.put(e.getCode(), true);
            if (e.getCode() == KeyCode.ESCAPE) {
                togglePause();
            }
        });
        canvas.setOnKeyReleased(e -> keys.put(e.getCode(), false));
        canvas.setOnMousePressed(e -> {
            if (!isPaused) {
                player.shoot(e.getX(), e.getY());
            }
        });
        canvas.setOnMouseDragged(e -> {
            if (!isPaused) {
                player.shoot(e.getX(), e.getY());
            }
        });
    }
    // Toggle the pause state of the game
    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            pauseGame();
        } else {
            resumeGame();
        }
    }
    // Pause the game by stopping the game loop and showing the pause menu
    private void pauseGame() {
        gameLoop.stop();
        root.getChildren().add(pauseMenu);
    }
    // Resume the game
    private void resumeGame() {
        root.getChildren().remove(pauseMenu);
        gameLoop.play();
        isPaused = false;
        canvas.requestFocus();
    }
    // Opens settings in menus
    private void openSettings() {
        gameLoop.stop(); // Pause the game while in settings
        Settings settings = new Settings(primaryStage, this);
        settings.show();
    }

    // Resizes screen according to the choice in settings
    public void resizeGame(int width, int height) {
        // Resize the canvas
        canvas.setWidth(width);
        canvas.setHeight(height);

        // Updates pause menu size
        if (pauseMenu != null) {
            pauseMenu.setPrefWidth(width);
            pauseMenu.setPrefHeight(height);
        }

        // Updates game over menu size
        if (gameOverMenu != null) {
            gameOverMenu.setPrefWidth(width);
            gameOverMenu.setPrefHeight(height);
        }

        // Resume the game
        if (!isPaused) {
            gameLoop.play();
        }

        // Request focus back to the canvas
        canvas.requestFocus();
    }
    private void returnToMainMenu() {
        gameLoop.stop();
        try {
            MainMenu mainMenu = new MainMenu();
            mainMenu.start(primaryStage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void restartGame() {
        gameLoop.stop();
        root.getChildren().remove(gameOverMenu);
        initializeGame();
    }

    // Timer on bullets
    public static void timerBullet(long time, Runnable r) {
        new Thread(() -> {
            try {
                Thread.sleep(time);
                r.run();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    // Spawn new enemies in the game on tents specifically
    public void spawnEnemies() {
        Thread spawner = new Thread(() -> {
            try {
                List<TerrainObject> tents = terrainManager.getTents();

                while (true) {
                    if (tents.isEmpty()) break;

                    // Get the next tent in the sequence (clockwise and then retunrs)
                    TerrainObject tent = tents.get(currentTentIndex);
                    double x = tent.x();
                    double y = tent.y();

                    // Adds small offset to prevent enemies from spawning at the same spot
                    double offsetX = (Math.random() - 0.5) * 20; // +/- 10 pixels
                    double offsetY = (Math.random() - 0.5) * 20;

                    // Spawn an enemy at the tent location with offset
                    Platform.runLater(() -> {
                        enemies.add(new Enemy(this.player, x + offsetX, y + offsetY, terrainManager));
                    });

                    // Move to next tent
                    currentTentIndex = (currentTentIndex + 1) % tents.size();

                    // Wait before spawning the next enemy
                    Thread.sleep(2000); // This can be changed according to the dev
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        });
        spawner.start();
    }
    // Updates game according to what happens
    private void update(GraphicsContext graphicsContext) {
        if (isPaused) {
            return;
        }

        // Check for win condition
        if (score >= 200 && !gameWon) {
            gameWon = true;
            handleGameOver();
            return;
        }
        // Check for death condition
        if (player.isDead() && player.isDeathAnimationComplete()) {
            gameWon = false;
            handleGameOver();
            return;
        }

        // Clamp player position to world bounds
        double clampedX = Math.max((double) -TerrainManager.WORLD_WIDTH /2,
                                   Math.min((double) TerrainManager.WORLD_WIDTH /2, player.getX()));
        double clampedY = Math.max((double) -TerrainManager.WORLD_HEIGHT /2,
                                   Math.min((double) TerrainManager.WORLD_HEIGHT /2, player.getY()));
        player.setPosition(clampedX, clampedY);

        // Update and clean up bullets that are out of bounds
        for (int i = Player.bullets.size() - 1; i >= 0; i--) {
            Bullet bullet = Player.bullets.get(i);
            if (bullet.getX() < (double) -TerrainManager.WORLD_WIDTH /2 ||
                    bullet.getX() > (double) TerrainManager.WORLD_WIDTH /2 ||
                    bullet.getY() < (double) -TerrainManager.WORLD_HEIGHT /2 ||
                    bullet.getY() > (double) TerrainManager.WORLD_HEIGHT /2) {
                Player.bullets.remove(i);
            }
        }

        camera.update(player);

        // Update terrain around the player's position
        terrainManager.updateTerrain(player.getX(), player.getY());

        // Render the base tile layer dynamically
        double tileWidth = terrainManager.getMainTile().getWidth();
        double tileHeight = terrainManager.getMainTile().getHeight();
        for (int x = 0; x < AppConfig.getWidth(); x += (int) tileWidth) {
            for (int y = 0; y < AppConfig.getHeight(); y += (int) tileHeight) {
                graphicsContext.drawImage(
                        terrainManager.getMainTile(),
                        x - camera.getOffsetX() % tileWidth,
                        y - camera.getOffsetY() % tileHeight
                );
            }
        }

        // Render dynamically generated terrain (grass, rocks, etc.)
        terrainManager.render(graphicsContext, camera);

        // Check and update bullets, enemies, player, etc.
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            e.render(graphicsContext, camera);

            // Check for bullet collisions with enemies and houses
            for (int j = 0; j < Player.bullets.size(); j++) {
                Bullet bullet = Player.bullets.get(j);
                if (terrainManager.collidesWithHouse(bullet.getX(), bullet.getY(), Bullet.WIDTH, Bullet.WIDTH)) {
                    Player.bullets.remove(j);
                    j--;  // Adjust bullet index after removal
                    continue;
                }
                if (e.collides(bullet.getX(), bullet.getY(), Enemy.WIDTH, Bullet.WIDTH)) {
                    Player.bullets.remove(j);
                    e.takeDamage();
                    score += 10;
                    break;
                }
            }

            if (e.isDead() && e.isDeathAnimationComplete()) {
                enemies.remove(i);
                i--; // Adjust index for removed enemy
            }
        }

        this.player.render(graphicsContext, camera);

        if (this.keys.getOrDefault(KeyCode.W, false)){
            this.player.move(0, -SPEED);
        }
        if (this.keys.getOrDefault(KeyCode.A, false)){
            this.player.move(-SPEED, 0);
        }
        if (this.keys.getOrDefault(KeyCode.S, false)){
            this.player.move(0, SPEED);
        }
        if (this.keys.getOrDefault(KeyCode.D, false)){
            this.player.move(SPEED, 0);
        }

        //DRAWS HP BAR
        graphicsContext.setFill(Color.GREEN);
        graphicsContext.fillRect(50, AppConfig.getHeight()-80, 100*(this.player.getHp()/100.0), 30);
        graphicsContext.setStroke(Color.BLACK);
        graphicsContext.strokeRect(50, AppConfig.getHeight()-80, 100, 30);

        // Display SCORE DYNAMICALLY
        double scoreX = AppConfig.getWidth() * 0.02; // 2% from left edge
        double scoreY = AppConfig.getHeight() * 0.08; // 8% from top
        double fontSize = AppConfig.getWidth() * 0.025; // Dynamic font size

        graphicsContext.setFont(Font.loadFont(Objects.requireNonNull(
                MainMenu.class.getResource("/joystix-monospace.otf")).toExternalForm(), fontSize));
        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillText("Score: " + score, scoreX, scoreY);
    }
}
