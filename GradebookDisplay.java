import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import java.util.ArrayList;
import java.util.List;

public class GradebookDisplay {
    @Parameters(commandDescription = "Display all grades for an assignment")
    private static class PrintAssignmentCommand {
        @Parameter(names = "-AN", description = "Assignment name", required = true)
        private List<String> assignmentName = new ArrayList<String>();

        @Parameter(names = "-A", description = "Display in order of student name")
        private boolean alphabeticalOrder;

        @Parameter(names = "-G", description = "Display in order of student grade")
        private boolean gradeOrder;
    }

    @Parameters(commandDescription = "Display all grades for a student")
    private static class PrintStudentCommand {
        @Parameter(names = "-FN", description = "Student first name", required = true)
        private List<String> studentFirstName = new ArrayList<String>();

        @Parameter(names = "-LN", description = "Student last name", required = true)
        private List<String> studentLastName = new ArrayList<String>();
    }

    @Parameters(commandDescription = "Calculate and display all final grades")
    private static class PrintFinalCommand {
        @Parameter(names = "-A", description = "Display in order of student name")
        private boolean alphabeticalOrder;

        @Parameter(names = "-G", description = "Display in order of student grade")
        private boolean gradeOrder;
    }

    @Parameter(names = "-N", description = "Gradebook filename", validateWith = CLIUtils.GradebookFileNameValidator.class, required = true)
    private String gradebook;

    @Parameter(names = "-K", description = "Encryption/Decryption key", validateWith = CLIUtils.KeyValidator.class, required = true)
    private String key;

    public static void main(String[] args) {
        int exitCode = 0;
        Gradebook gbook = null;

        try {
            GradebookDisplay app = new GradebookDisplay();
            PrintAssignmentCommand pa = new PrintAssignmentCommand();
            PrintStudentCommand ps = new PrintStudentCommand();
            PrintFinalCommand pf = new PrintFinalCommand();

            JCommander cmd = JCommander.newBuilder().addObject(app).addCommand("-PA", pa).addCommand("-PS", ps)
                    .addCommand("-PF", pf).build();
            cmd.parse(args);

            if (!(args[0].equals("-N") && args[2].equals("-K"))) {
                throw new ParameterException("Wrong flag order");
            }
            if (cmd.getParsedCommand() == null) {
                throw new ParameterException("No action specified");
            }

            gbook = Gradebook.load_and_decrypt(app.gradebook, app.key);

            if (cmd.getParsedCommand().equals("-PA")) {
                if (pa.alphabeticalOrder == pa.gradeOrder) {
                    throw new ParameterException("Exactly one order flag must be specified");
                }

                new CLIUtils.AssignmentNameValidator().validate("-AN", CLIUtils.getLast(pa.assignmentName));

                gbook.printAssignmentGrades(CLIUtils.getLast(pa.assignmentName), pa.alphabeticalOrder);
            }

            if (cmd.getParsedCommand().equals("-PS")) {
                new CLIUtils.StudentNameValidator().validate("-FN", CLIUtils.getLast(ps.studentFirstName));
                new CLIUtils.StudentNameValidator().validate("-LN", CLIUtils.getLast(ps.studentLastName));

                gbook.printStudentGrades(CLIUtils.getLast(ps.studentFirstName), CLIUtils.getLast(ps.studentLastName));
            }

            if (cmd.getParsedCommand().equals("-PF")) {
                if (pf.alphabeticalOrder == pf.gradeOrder) {
                    throw new ParameterException("Exactly one order flag must be specified");
                }

                gbook.printFinalGrades(pf.alphabeticalOrder);
            }
        } catch (ParameterException | Gradebook.AppError appError) {
            System.out.println("invalid");
            exitCode = 255;
        } catch (Gradebook.BackendError backendError) {
            backendError.printStackTrace();
        }

        if (gbook != null) {
            try {
                gbook.save_and_encrypt();
            } catch (Gradebook.BackendError backendError) {
                backendError.printStackTrace();
            }
        }

        System.exit(exitCode);
    }
}
