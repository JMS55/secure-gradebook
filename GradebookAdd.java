import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import java.util.ArrayList;
import java.util.List;

public class GradebookAdd {
    @Parameters(commandDescription = "Add a new assignment")
    private static class AddAssignmentCommand {
        @Parameter(names = "-AN", description = "Assignment name", required = true)
        private List<String> assignmentName = new ArrayList<String>();

        @Parameter(names = "-P", description = "Points assignment is out of", required = true)
        private List<String> points = new ArrayList<String>();

        @Parameter(names = "-W", description = "Assignment weight", required = true)
        private List<String> weight = new ArrayList<String>();
    }

    @Parameters(commandDescription = "Delete an existing assignment")
    private static class DeleteAssignmentCommand {
        @Parameter(names = "-AN", description = "Assignment name", required = true)
        private List<String> assignmentName = new ArrayList<String>();
    }

    @Parameters(commandDescription = "Add a new student")
    private static class AddStudentCommand {
        @Parameter(names = "-FN", description = "Student first name", required = true)
        private List<String> studentFirstName = new ArrayList<String>();

        @Parameter(names = "-LN", description = "Student last name", required = true)
        private List<String> studentLastName = new ArrayList<String>();
    }

    @Parameters(commandDescription = "Delete an existing student")
    private static class DeleteStudentCommand {
        @Parameter(names = "-FN", description = "Student first name", required = true)
        private List<String> studentFirstName = new ArrayList<String>();

        @Parameter(names = "-LN", description = "Student last name", required = true)
        private List<String> studentLastName = new ArrayList<String>();
    }

    @Parameters(commandDescription = "Add a new grade or overwrite an existing grade")
    private static class AddGradeCommand {
        @Parameter(names = "-FN", description = "Student first name", required = true)
        private List<String> studentFirstName = new ArrayList<String>();

        @Parameter(names = "-LN", description = "Student last name", required = true)
        private List<String> studentLastName = new ArrayList<String>();

        @Parameter(names = "-AN", description = "Assignment name", required = true)
        private List<String> assignmentName = new ArrayList<String>();

        @Parameter(names = "-G", description = "Number of points student received", required = true)
        private List<String> grade = new ArrayList<String>();
    }

    @Parameter(names = "-N", description = "Gradebook filename", validateWith = CLIUtils.GradebookFileNameValidator.class, required = true)
    private String gradebook;

    @Parameter(names = "-K", description = "Encryption/Decryption key", validateWith = CLIUtils.KeyValidator.class, required = true)
    private String key;

    public static void main(String[] args) {
        int exitCode = 0;
        Gradebook gbook = null;

        try {
            GradebookAdd app = new GradebookAdd();
            AddAssignmentCommand aa = new AddAssignmentCommand();
            DeleteAssignmentCommand da = new DeleteAssignmentCommand();
            AddStudentCommand as = new AddStudentCommand();
            DeleteStudentCommand ds = new DeleteStudentCommand();
            AddGradeCommand ag = new AddGradeCommand();

            JCommander cmd = JCommander.newBuilder().addObject(app).addCommand("-AA", aa).addCommand("-DA", da)
                    .addCommand("-AS", as).addCommand("-DS", ds).addCommand("-AG", ag).build();
            cmd.parse(args);

            if (!(args[0].equals("-N") && args[2].equals("-K"))) {
                throw new ParameterException("Wrong flag order");
            }
            if (cmd.getParsedCommand() == null) {
                throw new ParameterException("No action specified");
            }

            gbook = Gradebook.load_and_decrypt(app.gradebook, app.key);

            if (cmd.getParsedCommand().equals("-AA")) {
                new CLIUtils.AssignmentNameValidator().validate("-AN", CLIUtils.getLast(aa.assignmentName));
                new CLIUtils.NonNegativeIntegerValidator().validate("-P", CLIUtils.getLast(aa.points));
                new CLIUtils.AssignmentWeightValidator().validate("-W", CLIUtils.getLast(aa.weight));

                gbook.addAssignment(CLIUtils.getLast(aa.assignmentName), Integer.parseInt(CLIUtils.getLast(aa.points)),
                        Double.parseDouble(CLIUtils.getLast(aa.weight)));
            }

            if (cmd.getParsedCommand().equals("-DA")) {
                new CLIUtils.AssignmentNameValidator().validate("-AN", CLIUtils.getLast(da.assignmentName));

                gbook.deleteAssignment(CLIUtils.getLast(da.assignmentName));
            }

            if (cmd.getParsedCommand().equals("-AS")) {
                new CLIUtils.StudentNameValidator().validate("-FN", CLIUtils.getLast(as.studentFirstName));
                new CLIUtils.StudentNameValidator().validate("-LN", CLIUtils.getLast(as.studentLastName));

                gbook.addStudent(CLIUtils.getLast(as.studentFirstName), CLIUtils.getLast(as.studentLastName));
            }

            if (cmd.getParsedCommand().equals("-DS")) {
                new CLIUtils.StudentNameValidator().validate("-FN", CLIUtils.getLast(ds.studentFirstName));
                new CLIUtils.StudentNameValidator().validate("-LN", CLIUtils.getLast(ds.studentLastName));

                gbook.deleteStudent(CLIUtils.getLast(ds.studentFirstName), CLIUtils.getLast(ds.studentLastName));
            }

            if (cmd.getParsedCommand().equals("-AG")) {
                new CLIUtils.StudentNameValidator().validate("-FN", CLIUtils.getLast(ag.studentFirstName));
                new CLIUtils.StudentNameValidator().validate("-LN", CLIUtils.getLast(ag.studentLastName));
                new CLIUtils.AssignmentNameValidator().validate("-AN", CLIUtils.getLast(ag.assignmentName));
                new CLIUtils.NonNegativeIntegerValidator().validate("-G", CLIUtils.getLast(ag.grade));

                gbook.addGrade(CLIUtils.getLast(ag.studentFirstName), CLIUtils.getLast(ag.studentLastName),
                        CLIUtils.getLast(ag.assignmentName), Integer.parseInt(CLIUtils.getLast(ag.grade)));
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
