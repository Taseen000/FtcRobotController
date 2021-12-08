package org.firstinspires.ftc.teamcode.robots.reachRefactor.utils;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.hardware.motors.RevRobotics40HdHexMotor;
import com.qualcomm.robotcore.hardware.PIDCoefficients;
import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType;

import java.util.Arrays;

@Config(value = "FFConstants")
public class Constants {

    //----------------------------------------------------------------------------------------------
    // Control Constants
    //----------------------------------------------------------------------------------------------

    public static PIDCoefficients DRIVE_PID_COEFFICIENTS = new PIDCoefficients(0, 0, 0);
    public static PIDCoefficients ROTATE_PID_COEFFICIENTS = new PIDCoefficients(0.0055, 0, .13);
    public static PIDCoefficients SWIVEL_PID_COEFFICIENTS = new PIDCoefficients(0.1, 0, 0);
    public static PIDCoefficients CHASSIS_DISTANCE_PID_COEFFICIENTS = new PIDCoefficients(0.1, 0, 0);

    // small value used for the approximately equal calculation in MathUtils
    public static double EPSILON = 0.001;
    // gamepad trigger values below this threshold will be ignored
    public static double TRIGGER_DEADZONE = 0.2;
    // scales the target linear robot velocity from tele-op controls
    public static double FORWARD_SCALING_FACTOR = 5;
    // scales the target angular robot velocity from tele-op controls
    public static double ROTATE_SCALING_FACTOR = 40;

    // tele-op smoothing
    public static double FRONT_LEFT_SMOOTHING_FACTOR = 0.1;
    public static double FRONT_RIGHT_SMOOTHING_FACTOR = 0.1;
    public static double MIDDLE_SMOOTHING_FACTOR = 0.1;

    //----------------------------------------------------------------------------------------------
    // Physical Constants
    //----------------------------------------------------------------------------------------------

    // distance measurements
    public static double WHEEL_RADIUS = 0.1016;
    public static double TRACK_WIDTH = 0.308162;
    public static double DISTANCE_SENSOR_TO_FRONT_AXLE = 0.285;
    public static double DISTANCE_TARGET_TO_BACK_WHEEL = 0.075;
    public static double MIN_CHASSIS_LENGTH = 0; // TODO: find real minimum chassis length
    public static double MAX_CHASSIS_LENGTH = 0.864;
    // threshold to subtract from max chassis length when attempting to fully extend chassis
    // (to not give excessive strain to the linear slide)
    public static double CHASSIS_LENGTH_THRESHOLD = 0.1;

    // conversion factors
    public static double DRIVETRAIN_TICKS_PER_REVOLUTION = MotorConfigurationType.getMotorType(RevRobotics40HdHexMotor.class).getTicksPerRev();
    public static double DRIVETRAIN_TICKS_PER_METER = DRIVETRAIN_TICKS_PER_REVOLUTION / (2 * Math.PI * WHEEL_RADIUS);
    public static double INCHES_PER_METER = 39.3701;

    // physics constants
    public static double DRIVETRAIN_COEFFICIENT_OF_FRICTION = 0.2;
    public static double ACCELERATION_OF_GRAVITY = 9.80665; // m/s^2

    //----------------------------------------------------------------------------------------------
    // Dashboard Constants
    //----------------------------------------------------------------------------------------------

    public static String AXLE_STROKE_COLOR = "#4D934D";
    public static String TURN_RADIUS_STROKE_COLOR = "#FF0000";
    public static String WHEEL_STROKE_COLOR = "#006400";
    public static double DOTTED_LINE_DASH_LENGTH = 1;

    //----------------------------------------------------------------------------------------------
    // Miscellaneous
    //----------------------------------------------------------------------------------------------

    public enum GameState {
        TELE_OP("Tele-Op"), AUTONOMOUS("Autonomous"), TPM_CALIBRATION("TPM Calibration");

        private String name;

        GameState(String name) {
            this.name = name;
        }

        public static GameState getGameState(int index) {
            return GameState.values()[index];
        }

        public static int getNumGameStates() {
            return GameState.values().length;
        }

        public static int indexOf(GameState gameState) {
            return Arrays.asList(GameState.values()).indexOf(gameState);
        }
    }
    public enum Alliance {
        RED, BLUE
    }
    public static String DEFAULT_TELEMETRY_LINE = "Master";
    public static double AVERAGE_LOOP_TIME_SMOOTHING_FACTOR = 0.1;
    public static boolean DEFAULT_DASHBOARD_ENABLED = true;

}