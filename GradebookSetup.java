import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class GradebookSetup {

    @Parameter(names = "-N", description = "Gradebook filename", validateWith = CLIUtils.GradebookFileNameValidator.class, required = true)
    private String gradebook;

    public static void main(String[] args) {
        try {
            GradebookSetup app = new GradebookSetup();

            JCommander cmd = JCommander.newBuilder().addObject(app).build();
            cmd.parse(args);

            String key = Gradebook.create(app.gradebook);
            System.out.println("Key: " + key);
        } catch (ParameterException | Gradebook.AppError appError) {
            System.out.println("invalid");
            System.exit(255);
        } catch (Gradebook.BackendError backendError) {
            backendError.printStackTrace();
        }
    }
}
