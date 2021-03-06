/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.gazeplay.games.slidingpuzzle;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Dimension2D;
import javafx.scene.Parent;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.gazeplay.GameContext;
import net.gazeplay.commons.gaze.devicemanager.GazeEvent;
import net.gazeplay.commons.utils.stats.Stats;
import net.gazeplay.games.math101.Math101;

/**
 *
 * @author Peter Bardawil
 */
@Slf4j
public class slidingpuzzlecard extends Parent {

    public slidingpuzzlecard(int id, double positionX, double positionY, double width, double height, String fileName,
            double fixationlength, GameContext gameContext, slidingpuzzle gameInstance, Stats stats, double kingPosX,
            double kingPosY) {
        this.fixationlength = fixationlength;
        this.CardId = id;
        this.card = new Rectangle(positionX, positionY, width, height);
        this.card.setFill(new ImagePattern(new Image(fileName), 0, 0, 1, 1, true));
        this.gameContext = gameContext;
        this.initWidth = (int) width;
        this.initHeight = (int) height;
        this.initX = (int) positionX;
        this.initY = (int) positionY;
        this.gameInstance = gameInstance;
        this.stats = stats;
        this.progressIndicator = createProgressIndicator(width, height);
        this.getChildren().add(this.progressIndicator);
        this.getChildren().add(this.card);
        this.isKing = false;
        this.kingPosX = (int) kingPosX;
        this.kingPosY = (int) kingPosY;
        if (id != 9) {
            this.enterEvent = buildEvent();
        } else {
            this.enterEvent = buildEvent2();
        }
        gameContext.getGazeDeviceManager().addEventFilter(card);

        this.addEventFilter(MouseEvent.ANY, enterEvent);
        this.addEventFilter(GazeEvent.ANY, enterEvent);

        // Prevent null pointer exception
        currentTimeline = new Timeline();
    }

    private static final float zoom_factor = 1.05f;

    private final double fixationlength;

    private final Rectangle card;

    @Getter
    private final int CardId;

    private final GameContext gameContext;

    private final int initWidth;
    private final int initHeight;

    @Getter
    @Setter
    private int initX;

    @Getter
    @Setter
    private int initY;

    @Getter
    @Setter
    private int kingPosX, kingPosY;

    @Getter
    @Setter
    private boolean isKing;

    private final slidingpuzzle gameInstance;

    private final ProgressIndicator progressIndicator;

    private Timeline timelineProgressBar;

    final Stats stats;

    final EventHandler<Event> enterEvent;

    /**
     * Use a comma Timeline object so we can stop the current animation to prevent overlapses.
     */
    private Timeline currentTimeline;

    private ProgressIndicator createProgressIndicator(double width, double height) {
        ProgressIndicator indicator = new ProgressIndicator(0);
        indicator.setTranslateX(initX);
        indicator.setTranslateY(initY);
        indicator.setMinWidth(width * 0.1);
        indicator.setMinHeight(width * 0.1);
        indicator.setOpacity(0);
        return indicator;
    }

    private Boolean checkIfNeighbor() {

        if (this.initX == kingPosX && ((this.initY == kingPosY + initWidth) || (this.initY == kingPosY - initWidth)))
            return true;
        else if (this.initY == kingPosY
                && ((this.initX == kingPosX + initWidth) || (this.initX == kingPosX - initWidth)))
            return true;

        else
            return false;
    }

    private void isMyNeighborEvent() {

        progressIndicator.setOpacity(1);
        // currentTimeline.stop();
        currentTimeline = new Timeline();
        KeyValue xValue = new KeyValue(card.xProperty(), kingPosX);
        KeyValue yValue = new KeyValue(card.yProperty(), kingPosY);
        KeyFrame keyFrame = new KeyFrame(Duration.millis(100), xValue, yValue);
        currentTimeline.getKeyFrames().add(keyFrame);
        currentTimeline.play();

    }

    public void isKingCardEvent(double x, double y) {
        progressIndicator.setOpacity(0);
        // currentTimeline.stop();
        currentTimeline = new Timeline();
        KeyValue xValue = new KeyValue(card.xProperty(), x);
        KeyValue yValue = new KeyValue(card.yProperty(), y);
        KeyFrame keyFrame = new KeyFrame(Duration.millis(200), xValue, yValue);
        currentTimeline.getKeyFrames().add(keyFrame);
        currentTimeline.play();
    }

    private void onGameOver() {

        javafx.geometry.Dimension2D dimension2D = gameContext.getGamePanelDimensionProvider().getDimension2D();

        stats.incNbGoals();

        progressIndicator.setOpacity(0);

        // currentTimeline.stop();
        // currentTimeline = new Timeline();

        currentTimeline.onFinishedProperty().set(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {

                gameContext.playWinTransition(500, new EventHandler<ActionEvent>() {

                    @Override
                    public void handle(ActionEvent actionEvent) {
                        gameInstance.dispose();

                        gameContext.clear();

                        gameInstance.launch();

                        try {
                            stats.saveStats();
                        } catch (IOException ex) {
                            Logger.getLogger(slidingpuzzlecard.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        stats.notifyNewRoundReady();

                        gameContext.onGameStarted();
                    }
                });
            }
        });

        if (!currentTimeline.getStatus().equals(Timeline.Status.RUNNING))
            currentTimeline.playFromStart();
    }

    private EventHandler<Event> buildEvent() {

        return new EventHandler<Event>() {
            @Override
            public void handle(Event e) {

                if (!(currentTimeline.getStatus() == Animation.Status.RUNNING)
                        && (e.getEventType() == MouseEvent.MOUSE_ENTERED
                                || e.getEventType() == GazeEvent.GAZE_ENTERED)) {

                    progressIndicator.setOpacity(1);
                    progressIndicator.setProgress(0);

                    // currentTimeline.stop();
                    currentTimeline = new Timeline();

                    timelineProgressBar = new Timeline();

                    timelineProgressBar.getKeyFrames().add(new KeyFrame(new Duration(fixationlength),
                            new KeyValue(progressIndicator.progressProperty(), 1)));

                    currentTimeline.play();

                    timelineProgressBar.play();

                    timelineProgressBar.setOnFinished(new EventHandler<ActionEvent>() {

                        @Override
                        public void handle(ActionEvent actionEvent) {

                            if (checkIfNeighbor()) {
                                progressIndicator.setTranslateX(kingPosX);
                                progressIndicator.setTranslateY(kingPosY);

                                // gameInstance.showCards();

                                gameInstance.replaceCards(fixationlength, initX, initY, CardId);

                                isMyNeighborEvent();

                                gameInstance.fixCoord(CardId, initX, initY, kingPosX, kingPosY);
                                // gameInstance.showCards();

                                if (gameInstance.isGameOver())
                                    onGameOver();
                            }

                        }
                    });
                } else if (e.getEventType() == MouseEvent.MOUSE_EXITED || e.getEventType() == GazeEvent.GAZE_EXITED) {
                    timelineProgressBar.stop();
                    progressIndicator.setOpacity(0);
                    progressIndicator.setProgress(0);
                }
            }
        };
    }

    private EventHandler<Event> buildEvent2() {

        return new EventHandler<Event>() {
            @Override
            public void handle(Event e) {
            }
        };
    }
}
