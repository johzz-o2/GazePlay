package net.gazeplay.games.biboulejump;

import javafx.animation.*;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import lombok.extern.slf4j.Slf4j;
import net.gazeplay.GameContext;
import net.gazeplay.GameLifeCycle;
import net.gazeplay.commons.configuration.Configuration;
import net.gazeplay.commons.gaze.devicemanager.GazeEvent;
import net.gazeplay.commons.utils.ProgressButton;
import net.gazeplay.commons.utils.games.ImageLibrary;
import net.gazeplay.commons.utils.games.ImageUtils;
import net.gazeplay.commons.utils.games.Utils;
import net.gazeplay.commons.utils.multilinguism.Multilinguism;
import net.gazeplay.commons.utils.stats.Stats;

import java.io.*;
import java.util.*;

/**
 * Welcome to the Biboule Jump source code. I would advise you to stay away from it, I myself, the creator would much
 * rather do literally anything else than get back into this mess. But if you are courageous (or stupid?) enough, and
 * still want to tinker with the abominable physics involved, then please come on in, I applaud your bravery.
 */
@Slf4j
public class BibouleJump extends AnimationTimer implements GameLifeCycle {

    private static String DATA_PATH = "data/biboulejump";

    private final GameContext gameContext;
    private final BibouleJumpStats stats;
    private final Dimension2D dimensions;
    private final Random randomGenerator;
    private final Configuration config;
    private final int version;

    private final ImageLibrary bibouleImages;
    private final ImageLibrary cloudImages;

    private final Group backgroundLayer;
    private final Group middleLayer;
    private final Group foregroundLayer;
    private final Rectangle interactionOverlay;

    private Point2D gazeTarget;
    private Point2D velocity;
    private final double gravity = 0.005;
    private final double terminalVelocity = 0.8;
    private final double maxSpeed = 0.7;

    private final double platformWidth;
    private final double platformHeight;
    private Rectangle highestPlatform;

    private long lastTickTime = 0;
    private long minFPS = 1000;

    private Rectangle biboule;
    private Label onScreenText;
    private Text scoreText;
    private ArrayList<Platform> platforms;

    private int score;

    private final Rectangle shade;
    private final ProgressButton restartButton;
    private Text finalScoreText;
    private final int fixationLength;

    private final Multilinguism translate;

    public BibouleJump(GameContext gameContext, Stats stats, int version) {
        this.gameContext = gameContext;
        this.stats = (BibouleJumpStats) stats;
        this.dimensions = gameContext.getGamePanelDimensionProvider().getDimension2D();
        this.randomGenerator = new Random();
        this.config = Configuration.getInstance();
        this.version = version;

        bibouleImages = ImageUtils.createCustomizedImageLibrary(null, "biboulejump/biboules");
        cloudImages = ImageUtils.createCustomizedImageLibrary(null, "biboulejump/clouds");

        this.backgroundLayer = new Group();
        this.middleLayer = new Group();
        this.foregroundLayer = new Group();
        this.gameContext.getChildren().addAll(backgroundLayer, middleLayer, foregroundLayer);

        this.platforms = new ArrayList();
        this.platformHeight = dimensions.getHeight() / 10;
        this.platformWidth = dimensions.getWidth() / 7;

        this.translate = Multilinguism.getSingleton();

        Rectangle backgroundImage = new Rectangle(0, 0, dimensions.getWidth(), dimensions.getHeight());
        backgroundImage.setFill(Color.SKYBLUE);
        this.backgroundLayer.getChildren().add(backgroundImage);

        onScreenText = new Label();
        foregroundLayer.getChildren().add(onScreenText);

        scoreText = new Text(0, 50, "0");
        scoreText.setTextAlignment(TextAlignment.CENTER);
        scoreText.setFont(new Font(50));
        scoreText.setWrappingWidth(dimensions.getWidth());
        foregroundLayer.getChildren().add(scoreText);

        // Menu
        fixationLength = config.getFixationLength();

        shade = new Rectangle(0, 0, dimensions.getWidth(), dimensions.getHeight());
        shade.setFill(new Color(0, 0, 0, 0.75));

        restartButton = new ProgressButton();
        ImageView restartImage = new ImageView(DATA_PATH + "/menu/restart.png");
        restartImage.setFitHeight(dimensions.getHeight() / 6);
        restartImage.setFitWidth(dimensions.getHeight() / 6);
        restartButton.setImage(restartImage);
        restartButton.setLayoutX(dimensions.getWidth() / 2 - dimensions.getHeight() / 12);
        restartButton.setLayoutY(dimensions.getHeight() / 2 - dimensions.getHeight() / 12);
        restartButton.assignIndicator(event -> launch(), fixationLength);

        finalScoreText = new Text(0, dimensions.getHeight() / 4, "");
        finalScoreText.setFill(Color.WHITE);
        finalScoreText.setTextAlignment(TextAlignment.CENTER);
        finalScoreText.setFont(new Font(50));
        finalScoreText.setWrappingWidth(dimensions.getWidth());
        foregroundLayer.getChildren().addAll(shade, finalScoreText, restartButton);

        this.gameContext.getGazeDeviceManager().addEventFilter(restartButton);

        // Interaction
        gazeTarget = new Point2D(dimensions.getWidth() / 2.0, dimensions.getHeight() / 2.0);

        interactionOverlay = new Rectangle(0, 0, dimensions.getWidth(), dimensions.getHeight());

        EventHandler<Event> movementEvent = (Event event) -> {
            if (event.getEventType() == MouseEvent.MOUSE_MOVED) {
                gazeTarget = new Point2D(((MouseEvent) event).getX(), ((MouseEvent) event).getY());
            } else if (event.getEventType() == GazeEvent.GAZE_MOVED) {
                gazeTarget = interactionOverlay.screenToLocal(((GazeEvent) event).getX(), ((GazeEvent) event).getY());
            }
        };

        interactionOverlay.addEventFilter(MouseEvent.MOUSE_MOVED, movementEvent);
        interactionOverlay.addEventFilter(GazeEvent.GAZE_MOVED, movementEvent);
        interactionOverlay.setFill(Color.TRANSPARENT);
        foregroundLayer.getChildren().add(interactionOverlay);

        this.gameContext.getGazeDeviceManager().addEventFilter(interactionOverlay);
    }

    private void bounce(double intensity, String soundName) {
        velocity = new Point2D(velocity.getX(), -terminalVelocity * intensity);

        try {
            Utils.playSound(DATA_PATH + "/sounds/" + soundName);
        } catch (Exception e) {
            log.warn("Can't play sound: no associated sound : " + e.toString());
        }
    }

    /**
     * Sets up a new game by emptying out all the layers, and setting everything back to their initial values
     */
    @Override
    public void launch() {

        // hide end game menu
        shade.setOpacity(0);
        restartButton.disable();
        finalScoreText.setOpacity(0);

        interactionOverlay.setDisable(false);

        backgroundLayer.getChildren().removeAll(platforms);
        platforms.clear();

        this.middleLayer.getChildren().clear();
        biboule = new Rectangle(dimensions.getWidth() / 2, dimensions.getHeight() / 2, dimensions.getHeight() / 6,
                dimensions.getHeight() / 6);
        this.middleLayer.getChildren().add(biboule);
        biboule.setFill(new ImagePattern(bibouleImages.pickRandomImage()));

        velocity = Point2D.ZERO;
        score = 0;
        lastTickTime = 0;
        gazeTarget = new Point2D(dimensions.getWidth() / 2, 0);
        createPlatform(biboule.getX() + biboule.getWidth() / 2, biboule.getY() + biboule.getHeight() + platformHeight,
                false);

        generatePlatforms(dimensions.getHeight());

        this.start();
        stats.notifyNewRoundReady();
    }

    @Override
    public void dispose() {

    }

    /**
     * Caps the game speed, so it's not too fast
     * 
     * @return capped game speed
     */
    private double getGameSpeed() {
        double speed = config.getSpeedEffects();
        return speed <= 1.0 ? 1.0 : speed;
    }

    /**
     * It uses a dat file in the stat folder to save the highscores, the top 3 at the moment even though only the
     * highest score is displayed, a leaderboard system could be implemented. It reads the top 3 from the file, inserts
     * the new score, sorts it, and then takes the new top 3, and writes it in the file.
     * 
     * @param score
     *            new score
     * @return the highest score
     */
    private int getsetHighscore(int score) {

        File f = new File(Utils.getUserStatsFolder(config.getUserName()) + "/biboule-jump/highscores.dat");
        log.info("Highscore file: " + f.getAbsolutePath());
        try {
            ArrayList<Integer> highscores = new ArrayList();
            if (!f.createNewFile()) {
                Scanner scanner = new Scanner(f, "utf-8");
                scanner.useDelimiter(":");
                while (scanner.hasNextInt()) {
                    highscores.add(scanner.nextInt());
                }
            }
            highscores.add(score);

            Collections.sort(highscores);
            if (highscores.size() > 3) {
                highscores = new ArrayList(highscores.subList(highscores.size() - 3, highscores.size()));
            }

            Writer writer = new OutputStreamWriter(new FileOutputStream(f), "utf-8");
            for (int i : highscores)
                writer.write(i + ":");
            writer.close();

            return highscores.get(highscores.size() - 1);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return score;
    }

    /**
     * Upon the end of a game, a menu is displayed with the highscore and a restart button
     */
    private void death() {
        this.stop();
        // Show end menu (restart, quit, score)
        interactionOverlay.setDisable(true);
        shade.setOpacity(1);
        int highscore = getsetHighscore(score);
        StringBuilder sb = new StringBuilder();
        sb.append(translate.getTrad("Score", config.getLanguage()) + translate.getTrad("Colon", config.getLanguage())
                + " " + score + "\n");
        sb.append(translate.getTrad("Highscore", config.getLanguage())
                + translate.getTrad("Colon", config.getLanguage()) + " " + highscore + "\n");
        if (highscore <= score)
            sb.append(translate.getTrad("New highscore!", config.getLanguage()));
        finalScoreText.setText(sb.toString());
        finalScoreText.setOpacity(1);
        restartButton.active();
        stats.addRoundDuration();
    }

    /**
     * A bouncepad is just a smaller platform with a higher bounce factor set on top of another platform
     * 
     * @param platformX
     *            The other platform X position
     * @param platformY
     *            The other platform Y position
     */
    private void createBouncepad(double platformX, double platformY) {
        Platform b = new Platform(
                platformX + randomGenerator.nextInt((int) (platformWidth * 4 / 5)) - platformWidth / 2,
                platformY - platformHeight / 3, platformWidth / 5, platformHeight / 3, "boing.wav", 6);
        b.setFill(new ImagePattern(new Image(DATA_PATH + "/bouncepad.png")));
        backgroundLayer.getChildren().add(b);
        platforms.add(b);
    }

    /**
     * Creates a platform (cloud)
     * 
     * @param centerX
     *            The center position of the platform, the new X position must be calculated accordingly
     * @param centerY
     *            Same as X
     * @param moving
     *            moving platform or not
     */
    private void createPlatform(double centerX, double centerY, boolean moving) {
        Platform p;
        if (!moving) {
            p = new Platform(centerX - platformWidth / 2, centerY - platformHeight / 2, platformWidth, platformHeight,
                    "bounce.wav", 3, 0.5, 0, 0, 0);
        } else {
            p = new MovingPlatform(centerX - platformWidth / 2, centerY - platformHeight / 2, platformWidth,
                    platformHeight, "bounce.wav", 3, dimensions.getWidth(), getGameSpeed(), 0.5, 0, 0, 0);
        }
        highestPlatform = p;
        platforms.add(p);
        p.setFill(new ImagePattern(cloudImages.pickRandomImage()));
        backgroundLayer.getChildren().add(p);
    }

    /**
     * Generates platforms from bottom to top, and outside the window to anticipate scrolling. The platforms are
     * separated so they are not too far and reachable, and not too close and overlapping. At random times, a moving
     * platform is created, a bouncepad can also added to the platform
     * 
     * @param bottomLimit
     */
    private void generatePlatforms(double bottomLimit) {
        double top = -dimensions.getHeight();
        double bottom = bottomLimit;
        while (bottom > top) {
            double newPlatX;
            double newPlatY;
            do {
                newPlatX = randomGenerator.nextInt((int) (dimensions.getWidth() - platformWidth / 2))
                        + platformWidth / 2;
                newPlatY = bottom - randomGenerator.nextInt((int) (dimensions.getHeight() / 4));
            } while (Math.abs(newPlatX - highestPlatform.getX()) >= dimensions.getWidth() / 3);
            if (version == 0 && randomGenerator.nextInt(4) == 0) {
                createPlatform(newPlatX, newPlatY, true);
            } else {
                createPlatform(newPlatX, newPlatY, false);
                if (randomGenerator.nextInt(5) == 0) {
                    createBouncepad(newPlatX, newPlatY);
                }
            }

            bottom = newPlatY - 2 * platformHeight;
        }
    }

    /**
     * Takes a list of platforms, and pushes them down to create the scrolling effect If the platform has left the
     * window, it is removed
     * 
     * @param rects
     * @param difference
     */
    private void scrollList(ArrayList<Platform> rects, double difference) {
        Iterator<Platform> rectIter = rects.iterator();
        while (rectIter.hasNext()) {
            Platform p = rectIter.next();
            p.scroll(difference);
            if (p.getY() >= dimensions.getHeight()) {
                backgroundLayer.getChildren().remove(p);
                rectIter.remove();
            }
        }
    }

    /**
     * Main game loop This is called over and over, anytime it can be called, but the time between every call is not
     * equal so we need to compute the elapsed time since the last call, annd move objects on screen according to that
     * time in order to have smooth looking and consistent movement.
     * 
     * @param now
     *            Time when handle is called
     */
    @Override
    public void handle(long now) {

        if (lastTickTime == 0) {
            lastTickTime = now;
        }
        double timeElapsed = ((double) now - (double) lastTickTime) / Math.pow(10.0, 6.0); // in ms
        lastTickTime = now;

        String logs = "FPS: " + (int) (1000 / timeElapsed) + "\n";
        if (1000 / timeElapsed < minFPS) {
            minFPS = 1000 / (int) timeElapsed;
        }
        logs += "MinFPS: " + minFPS + "\n";
        logs += "Time elasped -- Real: " + timeElapsed;
        timeElapsed /= getGameSpeed();
        logs += timeElapsed + "\n";
        logs += "Speed effect: " + config.getSpeedEffects() + "\n";

        // Movement
        /// Gravity
        velocity = velocity.add(0, gravity * timeElapsed);
        if (velocity.getY() > terminalVelocity) {
            velocity = new Point2D(velocity.getX(), terminalVelocity);
        }

        /// Lateral mouvement
        double distance = Math.abs(gazeTarget.getX() - (biboule.getX() + biboule.getWidth() / 2));
        double direction = distance == 0 ? 1
                : (gazeTarget.getX() - (biboule.getX() + biboule.getWidth() / 2)) / distance;
        if (distance > maxSpeed) {
            velocity = new Point2D(maxSpeed * direction, velocity.getY());
        } else {
            velocity = new Point2D(distance * direction, velocity.getY());
        }
        /// Apply velocity
        biboule.setY(biboule.getY() + velocity.getY() * timeElapsed);
        biboule.setX(biboule.getX() + velocity.getX() * timeElapsed);

        // Collision detection
        if (velocity.getY() > 0) { // The biboule is falling
            Rectangle bibouleCollider = new Rectangle(biboule.getX() + biboule.getWidth() / 4,
                    biboule.getY() + biboule.getHeight() * 2 / 3, biboule.getWidth() / 2, biboule.getHeight() / 3);
            for (Platform p : platforms) {
                if (p.isColliding(bibouleCollider)) {
                    bounce(p.getBounceFactor(), p.getSoundFileLocation());
                }
            }

        }

        // Scrolling
        if (biboule.getY() <= dimensions.getHeight() / 3) {
            double difference = dimensions.getHeight() / 3 - biboule.getY();
            updateScore(difference);
            scrollList(platforms, difference);
            biboule.setY(biboule.getY() + difference);
        }

        if (highestPlatform.getY() >= -dimensions.getHeight() / 2)
            generatePlatforms(highestPlatform.getY());

        // Fall out of screen
        if (biboule.getY() >= dimensions.getHeight()) {
            death();
        }

        // Uncomment to show on screen logs
        // onScreenText.setText(logs);
    }

    /**
     * Updates the score according to the pixel difference scrolled upwards by the player
     * 
     * @param difference
     */
    private void updateScore(double difference) {
        int inc = (int) (difference / dimensions.getHeight() * 100);
        score += inc;
        stats.incNbGoals(inc);
        scoreText.setText(String.valueOf(score));
        scoreText.setX(dimensions.getWidth() / 2 - scoreText.getWrappingWidth() / 2);
    }

}
