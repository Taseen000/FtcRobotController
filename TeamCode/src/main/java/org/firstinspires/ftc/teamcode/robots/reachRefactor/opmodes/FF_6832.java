package org.firstinspires.ftc.teamcode.robots.reachRefactor.opmodes;

import org.firstinspires.ftc.teamcode.robots.reachRefactor.subsystems.Crane;
import static org.firstinspires.ftc.teamcode.robots.reachRefactor.utils.Constants.*;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.Gamepad;

import org.firstinspires.ftc.teamcode.robots.reachRefactor.subsystems.Robot;
import org.firstinspires.ftc.teamcode.robots.reachRefactor.utils.ExponentialSmoother;
import org.firstinspires.ftc.teamcode.robots.reachRefactor.utils.StickyGamepad;
import org.firstinspires.ftc.teamcode.robots.reachRefactor.utils.TelemetryProvider;
import org.firstinspires.ftc.teamcode.robots.reachRefactor.utils.UtilMethods;
import org.firstinspires.ftc.teamcode.robots.reachRefactor.vision.VisionProviders;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Controls
 * --Pregame--
 * left bumper - decrement state
 * right bumper - increment state
 * start - toggle active
 * x - set alliance to blue
 * b - set alliance to red
 * a - set starting location to lower position
 * y - set starting location to upper position
 *
 * dpad up - initialize / shutdown vision provider
 * dpad left - increment vision provider index
 * dpad down - toggle debug telemetry
 * dpad right - save vision output to filesystem
 *
 * --Tele-Op--
 * gamepad 1: x - set gripper for intake
 * gamepad 1: b - lift gripper
 * gamepad 1: y - transfer
 * gamepad 1: a - toggle duck spinner
 * gamepad 1: tank drive
 *
 * gamepad 2: x - articulate crane to home
 * gamepad 2: b - dump crane bucket
 * gamepad 2: a - toggle duck spinner
 * gamepad 2: y - transfer
 * gamepad 2: dpad right - rotate turret 90 degrees right
 * gamepad 2: dpad up - articulate crane to high tier
 * gamepad 2: dpad left - articulate crane to middle tier
 * gamepad 2: dpad down - articulate crane to lowest tier
 * gamepad 2: left bumper - decrement chassis length stage
 * gamepad 2: right bumper - increment chassis length stage
 * gamepad 2: arcade drive
 *
 * guide - emergency stop
 *
 * --Manual Diagnostic--
 * gamepad 1: left bumper - decrement diagnostic index
 * gamepad 1: right bumper - increment diagnostic index
 * gamepad 1: right stick y - fine adjustment
 * gamepad 1: left stick y - coarse adjustment
 */
@Config
@TeleOp(name = "refactored FF_6832")
public class FF_6832 extends OpMode {
    // constants
    public static double TANK_DRIVE_JOYSTICK_DIFF_DEADZONE = 0.3;
    public static double AVERAGE_LOOP_TIME_SMOOTHING_FACTOR = 0.1;
    public static boolean DEFAULT_DEBUG_TELEMETRY_ENABLED = false;
    public static double FORWARD_SCALING_FACTOR = 48; // scales the target linear robot velocity from tele-op controls
    public static double ROTATE_SCALING_FACTOR = FORWARD_SCALING_FACTOR * (2 / TRACK_WIDTH); // scales the target angular robot velocity from tele-op controls
    public static double[] CHASSIS_LENGTH_LEVELS = new double[] {
            MIN_CHASSIS_LENGTH,
            MIN_CHASSIS_LENGTH + (MAX_CHASSIS_LENGTH - MIN_CHASSIS_LENGTH) / 3,
            MIN_CHASSIS_LENGTH + 2 * (MAX_CHASSIS_LENGTH - MIN_CHASSIS_LENGTH) / 3,
            MAX_CHASSIS_LENGTH
    };
    public static int DIAGNOSTIC_SERVO_STEP_MULTIPLER_SLOW = 5;
    public static int DIAGNOSTIC_SERVO_STEP_MULTIPLER_FAST = 15;

    private Robot robot;
    private Autonomous auto;
    private FtcDashboard dashboard;

    // global state
    private boolean active, initializing, debugTelemetryEnabled;
    private Alliance alliance;
    private Position startingPosition;
    private GameState gameState;
    private int gameStateIndex;
    private StickyGamepad stickyGamepad1, stickyGamepad2;
    private boolean gamepad1JoysticksActive, gamepad2JoysticksActive;

    // vision state
    private int visionProviderIndex;
    private boolean visionProviderFinalized;

    // tele-op state
    private int chassisDistanceLevelIndex;
    private double forward, rotate;

    // diagnostic state
    private DiagnosticStep diagnosticStep;
    private int diagnosticIndex;
    private boolean diagnosticFinished;
    private int servoTargetPos;

    // timing
    private long lastLoopClockTime, loopTime;
    private double averageLoopTime;
    private ExponentialSmoother loopTimeSmoother;

    public enum GameState {
        TELE_OP("Tele-Op"),
        AUTONOMOUS("Autonomous"),
        AUTONOMOUS_DIAGNOSTIC("Autonomous Diagnostic"),
        MANUAL_DIAGNOSTIC("Manual Diagnostic"),
        BACK_AND_FORTH("Back And Forth"),
        SQUARE("Square");

        private final String name;

        GameState(String name) {
            this.name = name;
        }

        public String getName() { return name; }

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

    // Code to run ONCE when the driver hits INIT
    @Override
    public void init() {
        // global state
        active = true;
        initializing = true;
        debugTelemetryEnabled = DEFAULT_DEBUG_TELEMETRY_ENABLED;
        gameState = GameState.TELE_OP;

        // timing
        lastLoopClockTime = System.nanoTime();
        loopTimeSmoother = new ExponentialSmoother(AVERAGE_LOOP_TIME_SMOOTHING_FACTOR);

        // gamepads
        stickyGamepad1 = new StickyGamepad(gamepad1);
        stickyGamepad2 = new StickyGamepad(gamepad2);

        robot = new Robot(hardwareMap, true);
        alliance = Alliance.BLUE;
        startingPosition = Position.START_BLUE_UP;
        robot.driveTrain.setPoseEstimate(startingPosition.getPose());
        auto = new Autonomous(robot);

        // vision
        auto.createVisionProvider(VisionProviders.DEFAULT_PROVIDER_INDEX);

        auto.visionProvider.initializeVision(hardwareMap);
        visionProviderFinalized = true;

        dashboard = FtcDashboard.getInstance();
        dashboard.setTelemetryTransmissionInterval(25);
        telemetry.setMsTransmissionInterval(25);

        robot.articulate(Robot.Articulation.INIT);
    }

    private void handleStateSwitch() {
        if (!active) {
            if (stickyGamepad1.left_bumper || stickyGamepad2.left_bumper)
                gameStateIndex -= 1;
            if (stickyGamepad1.right_bumper || stickyGamepad2.right_bumper)
                gameStateIndex += 1;

            if(gameStateIndex < 0)
                gameStateIndex = GameState.getNumGameStates() - 1;
            gameStateIndex %= GameState.getNumGameStates();
            gameState = GameState.getGameState(gameStateIndex);
        }

        if (stickyGamepad1.start || stickyGamepad2.start)
            active = !active;
    }

    private void handleVisionProviderSwitch() {
        if(!active) {
            if(!visionProviderFinalized) {
                if (stickyGamepad1.dpad_left || stickyGamepad2.dpad_left) {
                    visionProviderIndex = (visionProviderIndex + 1) % VisionProviders.VISION_PROVIDERS.length; // switch vision provider
                    auto.createVisionProvider(visionProviderIndex);
                }
                if (stickyGamepad1.dpad_up || stickyGamepad2.dpad_up) {
                    auto.visionProvider.initializeVision(hardwareMap); // this is blocking
                    visionProviderFinalized = true;
                }
            } else if (stickyGamepad1.dpad_up || stickyGamepad2.dpad_up) {
                auto.visionProvider.shutdownVision(); // also blocking, but should be very quick
                visionProviderFinalized = false;
            }
        }
        else if((stickyGamepad1.dpad_right || stickyGamepad2.dpad_right) && visionProviderFinalized)
        {
            auto.visionProvider.saveDashboardImage();
        }
    }

    private void handlePregameControls() {
        if(stickyGamepad1.x || stickyGamepad2.x) {
            alliance = Alliance.BLUE;
            startingPosition = Position.START_BLUE_UP;
        }
        if(stickyGamepad1.b || stickyGamepad2.b) {
            alliance = Alliance.RED;
            startingPosition = Position.START_RED_UP;
        }
        if(stickyGamepad1.y || stickyGamepad2.y)
            startingPosition =
                    alliance == Alliance.RED ?
                            Position.START_RED_UP :
                            Position.START_BLUE_UP;
        if(stickyGamepad1.a || stickyGamepad2.a)
            startingPosition =
                    alliance == Alliance.RED ?
                            Position.START_RED_DOWN :
                            Position.START_BLUE_DOWN;

        if(stickyGamepad1.dpad_down || stickyGamepad2.dpad_down)
            debugTelemetryEnabled = !debugTelemetryEnabled;
    }

    // Code to run REPEATEDLY after the driver hits INIT, but before they hit PLAY
    @Override
    public void init_loop() {
        handleStateSwitch();
        handleVisionProviderSwitch();
        handlePregameControls();

        update();
    }

    //Code that runs ONCE after the driver hits PLAY
    @Override
    public void start() {
        initializing = false;

        auto.build();
        auto.visionProvider.shutdownVision();

        robot.articulate(Robot.Articulation.START);
        robot.driveTrain.setMaintainChassisLengthEnabled(true);
        robot.driveTrain.setAntiTippingEnabled(false);
        robot.driveTrain.setChassisLength(CHASSIS_LENGTH_LEVELS[0]);
        lastLoopClockTime = System.nanoTime();
    }


    private void handleArcadeDrive(Gamepad gamepad) {
        forward = -gamepad.left_stick_y * FORWARD_SCALING_FACTOR;
        rotate = -gamepad.right_stick_x * ROTATE_SCALING_FACTOR;
    }

    private void handleTankDrive(Gamepad gamepad) {
        double left = -gamepad.left_stick_y;
        double right = -gamepad.right_stick_y;

        forward = (right + left) / 2 * FORWARD_SCALING_FACTOR;
        rotate = -(right - left) / 2 * ROTATE_SCALING_FACTOR;

        if(Math.abs(right - left) < TANK_DRIVE_JOYSTICK_DIFF_DEADZONE)
            rotate = 0;
    }

    private void sendDriveCommands() {
        robot.driveTrain.setDrivePower(new Pose2d(forward, 0, rotate));
    }

    private void handleTeleOp() { // apple
        // gamepad 1
        if (stickyGamepad1.x)
            robot.gripper.set();
        if(stickyGamepad1.b)
            robot.gripper.lift();

        if(stickyGamepad1.a)
            robot.driveTrain.toggleDuckSpinner(alliance.getMod());

        if (stickyGamepad1.y)
            robot.articulate(Robot.Articulation.TRANSFER);
        if(stickyGamepad1.dpad_right)
            robot.crane.articulate(Crane.Articulation.HOME);

        if(stickyGamepad1.dpad_right)
            robot.crane.articulate(Crane.Articulation.HOME);

        // gamepad 2

        if(stickyGamepad2.x) //go home - it's the safest place to retract if the bucket is about to colide with something
            robot.crane.articulate(Crane.Articulation.HOME);

        if(stickyGamepad2.b) //dump bucket - might be able to combine this with Cycle Complete
            robot.crane.dump();

        if(stickyGamepad2.a) //spin carousel
            robot.driveTrain.toggleDuckSpinner(alliance.getMod());

        if(stickyGamepad2.dpad_right)
           robot.crane.articulate(Crane.Articulation.HOME);
        if(stickyGamepad2.dpad_down)
            robot.crane.articulate(Crane.Articulation.LOWEST_TIER);
        if(stickyGamepad2.dpad_left)
            robot.crane.articulate(Crane.Articulation.MIDDLE_TIER);
        if(stickyGamepad2.dpad_up)  //High Tier
            robot.crane.articulate(Crane.Articulation.HIGH_TIER);
        if(stickyGamepad2.y) //todo - this should trigger a Swerve_Cycle_Complete articulation in Pose
            robot.articulate(Robot.Articulation.TRANSFER);

        if(stickyGamepad2.right_bumper)
            chassisDistanceLevelIndex++;
        else if(stickyGamepad2.left_bumper)
            chassisDistanceLevelIndex--;

        chassisDistanceLevelIndex = Math.abs(chassisDistanceLevelIndex % CHASSIS_LENGTH_LEVELS.length);
        robot.driveTrain.setChassisLength(CHASSIS_LENGTH_LEVELS[chassisDistanceLevelIndex]);

        if(gamepad1JoysticksActive && !gamepad2JoysticksActive)
            handleTankDrive(gamepad1);
        else if (gamepad2JoysticksActive && !gamepad1JoysticksActive)
            handleArcadeDrive(gamepad2);
        else {
            forward = 0;
            rotate = 0;
        }
        sendDriveCommands();

        if(stickyGamepad1.guide || stickyGamepad2.guide)
            robot.stop();
    }

    private enum DiagnosticStep {
        DRIVETRAIN_LEFT_MOTOR, DRIVETRAIN_RIGHT_MOTOR, DRIVETRAIN_MIDDLE_MOTOR, DRIVETRAIN_MIDDLE_SWIVEL_MOTOR,
        CRANE_SHOULDER_SERVO, CRANE_ELBOW_SERVO, CRANE_WRIST_SERVO, TURRET_MOTOR,
        GRIPPER_SERVO, GRIPPER_PITCH_SERVO,
        DUCK_SPINNER;

        public static DiagnosticStep getDiagnosticStep(int index) {
            return DiagnosticStep.values()[index];
        }

        public static int getNumDiagnosticSteps() {
            return DiagnosticStep.values().length;
        }
    }

    private void handleDiagnosticMotorControls(DoubleConsumer setTargetVelocity) {
        setTargetVelocity.accept(-gamepad1.right_stick_y);
    }

    private void handleDiagnosticServoControls(IntSupplier getTargetPos, IntConsumer setTargetPos) {
        servoTargetPos = getTargetPos.getAsInt();
        if(UtilMethods.notJoystickDeadZone(gamepad1.right_stick_y))
            setTargetPos.accept(UtilMethods.servoClip((int) (getTargetPos.getAsInt() - gamepad1.right_stick_y * DIAGNOSTIC_SERVO_STEP_MULTIPLER_SLOW)));
        else if(UtilMethods.notJoystickDeadZone(gamepad1.left_stick_y))
            setTargetPos.accept(UtilMethods.servoClip((int) (getTargetPos.getAsInt() - gamepad1.left_stick_y * DIAGNOSTIC_SERVO_STEP_MULTIPLER_FAST)));
    }

    private void handleManualDiagnostic() {
        if(stickyGamepad1.right_bumper)
            diagnosticIndex++;
        else if(stickyGamepad1.left_bumper)
            diagnosticIndex--;
        if(diagnosticIndex < 0)
            diagnosticIndex = DiagnosticStep.getNumDiagnosticSteps() - 1;
        diagnosticIndex %= DiagnosticStep.getNumDiagnosticSteps();
        diagnosticStep = DiagnosticStep.getDiagnosticStep(diagnosticIndex);

        switch(diagnosticStep) {
            case DRIVETRAIN_LEFT_MOTOR:
                handleDiagnosticMotorControls(robot.driveTrain::setLeftVelocity);
                break;
            case DRIVETRAIN_RIGHT_MOTOR:
                handleDiagnosticMotorControls(robot.driveTrain::setRightVelocity);
                break;
            case DRIVETRAIN_MIDDLE_MOTOR:
                handleDiagnosticMotorControls(robot.driveTrain::setSwerveVelocity);
                break;
            case DRIVETRAIN_MIDDLE_SWIVEL_MOTOR:
                robot.driveTrain.setSwivelAngle(-gamepad1.right_stick_y);
                break;
            case CRANE_SHOULDER_SERVO:
                handleDiagnosticServoControls(robot.crane::getShoulderTargetPos, robot.crane::setShoulderTargetPosRaw);
                break;
            case CRANE_ELBOW_SERVO:
                handleDiagnosticServoControls(robot.crane::getElbowTargetPos, robot.crane::setElbowTargetPosRaw);
                break;
            case CRANE_WRIST_SERVO:
                handleDiagnosticServoControls(robot.crane::getWristTargetPos, robot.crane::setWristTargetPosRaw);
                break;
            case TURRET_MOTOR:
                robot.crane.turret.setTargetAngle(robot.crane.turret.getTargetAngle() - gamepad1.right_stick_y);
                break;
            case GRIPPER_SERVO:
                handleDiagnosticServoControls(robot.gripper::getTargetPos, robot.gripper::setTargetPosDiag);
                break;
            case GRIPPER_PITCH_SERVO:
                handleDiagnosticServoControls(robot.gripper::getPitchTargetPos, robot.gripper::setPitchTargetPosDiag);
                break;
            case DUCK_SPINNER:
                robot.driveTrain.setDuckSpinnerPower(-gamepad1.right_stick_y);
                break;
        }
    }

    //Main loop that repeats after hitting PLAY
    @Override
    public void loop() {
        handleStateSwitch();

        if (active) {
            switch(gameState) {
                case TELE_OP:
                    handleTeleOp();
                    break;
                case AUTONOMOUS:
                    if (alliance == Alliance.RED && auto.autonomousRed.execute()) {
                        active = false;
                        gameState = GameState.TELE_OP;
                        gameStateIndex = GameState.indexOf(GameState.TELE_OP);
                    } else if (alliance == Alliance.BLUE && auto.autonomousBlue.execute()) {
                        active = false;
                        gameState = GameState.TELE_OP;
                        gameStateIndex = GameState.indexOf(GameState.TELE_OP);
                    }
                    break;
                case AUTONOMOUS_DIAGNOSTIC:
                    if(!diagnosticFinished && robot.articulate(Robot.Articulation.DIAGNOSTIC))
                            diagnosticFinished = true;
                    break;
                case MANUAL_DIAGNOSTIC:
                    handleManualDiagnostic();
                    break;
                case BACK_AND_FORTH:
                    auto.backAndForth.execute();
                    break;
                case SQUARE:
                    auto.square.execute();
                    break;
            }
        } else {
            handleStateSwitch();
            handlePregameControls();
        }

        update();
    }

    private void updateTiming() {
        long loopClockTime = System.nanoTime();
        loopTime = loopClockTime - lastLoopClockTime;
        averageLoopTime = loopTimeSmoother.update(loopTime);
        lastLoopClockTime = loopClockTime;
    }

    private void handleTelemetry(Map<String, Object> telemetryMap, String telemetryName, TelemetryPacket packet) {
        telemetry.addLine(telemetryName);
        packet.addLine(telemetryName);

        for (Map.Entry<String, Object> entry : telemetryMap.entrySet()) {
            String line = String.format("%s: %s", entry.getKey(), entry.getValue());
            telemetry.addLine(line);
            packet.put(entry.getKey(), entry.getValue());
        }

        telemetry.addLine();
        packet.addLine("");
    }

    private boolean joysticksActive(Gamepad gamepad) {
        return  UtilMethods.notJoystickDeadZone(gamepad.left_stick_x) ||
                UtilMethods.notJoystickDeadZone(gamepad.left_stick_y) ||
                UtilMethods.notJoystickDeadZone(gamepad.right_stick_x) ||
                UtilMethods.notJoystickDeadZone(gamepad.right_stick_y);
    }

    private void update() {
        if(initializing) {
            auto.visionProvider.update();
            robot.driveTrain.setPoseEstimate(startingPosition.getPose());
        }

        gamepad1JoysticksActive = joysticksActive(gamepad1);
        gamepad2JoysticksActive = joysticksActive(gamepad2);

        stickyGamepad1.update();
        stickyGamepad2.update();

        TelemetryPacket packet = new TelemetryPacket();
        Map<String, Object> opModeTelemetryMap = new HashMap<>();

        // handling op mode telemetry
        if(initializing) {
            opModeTelemetryMap.put("Starting Position", startingPosition);
        }
        opModeTelemetryMap.put("Average Loop Time", String.format("%d ms (%d hz)", (int) (averageLoopTime * 1e-6), (int) (1 / (averageLoopTime * 1e-9))));
        opModeTelemetryMap.put("Last Loop Time", String.format("%d ms (%d hz)", (int) (loopTime * 1e-6), (int) (1 / (loopTime * 1e-9))));
        opModeTelemetryMap.put("Active", active);
        opModeTelemetryMap.put("Chassis Level Index", String.format("%d / %d", chassisDistanceLevelIndex, CHASSIS_LENGTH_LEVELS.length));

        switch(gameState) {
            case TELE_OP:
                break;
            case AUTONOMOUS:
                break;
            case AUTONOMOUS_DIAGNOSTIC:
                break;
            case MANUAL_DIAGNOSTIC:
                opModeTelemetryMap.put("Diagnostic Step", diagnosticStep);
                opModeTelemetryMap.put("Servo Target Pos", servoTargetPos);
                break;
        }
        handleTelemetry(opModeTelemetryMap,  String.format("(%d): %s", gameStateIndex, gameState.getName()), packet);

        // handling subsystem telemetry
        for(TelemetryProvider telemetryProvider: robot.subsystems)
            handleTelemetry(telemetryProvider.getTelemetry(debugTelemetryEnabled), telemetryProvider.getTelemetryName(), packet);
        handleTelemetry(robot.getTelemetry(debugTelemetryEnabled), robot.getTelemetryName(), packet);

        // handling vision telemetry
        handleTelemetry(auto.visionProvider.getTelemetry(debugTelemetryEnabled), auto.visionProvider.getTelemetryName(), packet);

        robot.update(packet.fieldOverlay());

        dashboard.sendTelemetryPacket(packet);
        telemetry.update();

        updateTiming();
    }
}