package commands.classic;

import commands.model.AbstractCommand;
import data.Constants;
import data.JobUser;
import data.User;
import enums.Job;
import enums.Language;
import exceptions.DiscordException;
import exceptions.NotFoundDiscordException;
import util.Message;
import exceptions.TooMuchDiscordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import util.Translator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Created by steve on 14/07/2016.
 */
public class JobCommand extends AbstractCommand {

    private final static Logger LOG = LoggerFactory.getLogger(JobCommand.class);
    private DiscordException tooMuchJobs;
    private DiscordException notFoundJob;
    private DiscordException notFoundLevel;

    public JobCommand(){
        super("job", "(\\s+([\\p{L}|\\W]+|-all)(\\s+\\d{1,3})?)?");
        setUsableInMP(false);
        tooMuchJobs = new TooMuchDiscordException("exception.toomuch.jobs", "exception.toomuch.jobs_found", true);
        notFoundJob = new NotFoundDiscordException("exception.notfound.job", "exception.notfound.job_found");
        notFoundLevel = new NotFoundDiscordException("exception.notfound.level", "exception.notfound.level_found");
    }

    @Override
    public boolean request(IMessage message) {
        if (super.request(message)) {
            Language lg = Translator.getLanguageFrom(message.getChannel());
            Matcher m = getMatcher(message);
            m.find();

            if (m.group(1) == null){
                StringBuilder st = new StringBuilder(Translator.getLabel(lg, "job.request.1")).append(" :\n```");
                for(Job job : Job.values())
                    st.append("\n- ").append(job.getLabel(lg).replace("{0}", ""));
                st.append("```");
                Message.sendText(message.getChannel(), st.toString());
            }
            else if (!m.group(2).equals("-all")) {
                List<String> jobs = getJob(lg, m.group(2));

                if (jobs.size() == 1) {
                    if (m.group(3) != null) { // setting data
                        User author = User.getUser(message.getGuild(), message.getAuthor());
                        int level = Integer.parseInt(m.group(3).replaceAll("\\W+", ""));
                        if (!author.getJobs().containsKey(jobs.get(0)))
                            new JobUser(jobs.get(0), level, author).addToDatabase();
                        else
                            author.getJobs().get(jobs.get(0)).setLevel(level);

                        if (author.getJob(jobs.get(0)) > 0)
                            Message.sendText(message.getChannel(), Translator.getLabel(lg, "job.request.2")
                                .replace("{user}", author.getName())
                                .replace("{job}", Translator.getLabel(lg, jobs.get(0)).replace("{0}", ""))
                                .replace("{level}", String.valueOf(author.getJob(jobs.get(0)))));
                        else
                            Message.sendText(message.getChannel(), Translator.getLabel(lg, "job.request.3")
                                    .replace("{user}", author.getName())
                                    .replace("{job}", Translator.getLabel(lg, jobs.get(0)).replace("{0}", "")));
                    } else { // Consultation
                        List<User> artisans = new ArrayList<>();

                        for (IUser iUser : message.getGuild().getUsers()) {
                            User user = User.getUser(message.getGuild(), iUser);
                            if (user.getJob(jobs.get(0)) > 0)
                                artisans.add(user);
                        }

                        artisans.sort((User o1, User o2)->{
                                if (o2.getJob(jobs.get(0)) != o1.getJob(jobs.get(0)))
                                    return o2.getJob(jobs.get(0)) - o1.getJob(jobs.get(0));
                                return o1.getName().compareTo(o2.getName());
                            }
                        );

                        StringBuilder st = new StringBuilder();

                        if (!artisans.isEmpty()) {
                            st.append(Translator.getLabel(lg, "job.request.4")
                                    .replace("{job}", Translator.getLabel(lg, jobs.get(0)).replace("{0}", "s"))
                                    .replace("{guild}",  message.getGuild().getName()))
                              .append("\n```");

                            for (User user : artisans) {
                                st.append("\n").append(user.getName());
                                for (int i = user.getName().length(); i < (Constants.nicknameLimit + 10); i++)
                                    st.append(" ");
                                String level = String.valueOf(user.getJob(jobs.get(0)));
                                for (int i = level.length(); i < 3; i++)
                                    st.append(" ");
                                st.append(level);
                            }
                            st.append("```");
                        } else
                            st.append(Translator.getLabel(lg, "job.request.5")
                                    .replace("{job}", Translator.getLabel(lg, jobs.get(0)).replace("{0}", "")));

                        Message.sendText(message.getChannel(), st.toString());
                    }
                } else if (jobs.size() > 1)
                    tooMuchJobs.throwException(message, this, lg, jobs);
                else
                    notFoundJob.throwException(message, this, lg);
            }
            else if (m.group(3) != null){ // add all jobs for the user
                User author = User.getUser(message.getGuild(), message.getAuthor());
                int level = Integer.parseInt(m.group(3).replaceAll("\\W+", ""));

                for(Job job : Job.values()) {
                    if (!author.getJobs().containsKey(job.getName()))
                        new JobUser(job.getName(), level, author).addToDatabase();
                    else
                        author.getJobs().get(job.getName()).setLevel(level);
                }
                if (level > 0)
                    Message.sendText(message.getChannel(), Translator.getLabel(lg, "job.request.6")
                            .replace("{user}", author.getName()));
                else
                    Message.sendText(message.getChannel(), Translator.getLabel(lg, "job.request.7")
                            .replace("{user}", author.getName()));
            }
            else
                notFoundLevel.throwException(message, this, lg);
        }

        return false;
    }

    private List<String> getJob(Language lg, String nameProposed){
        nameProposed = Normalizer.normalize(nameProposed, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
        nameProposed = nameProposed.replaceAll("\\W+", "");
        List<String> jobs = new ArrayList<>();

        for(Job job : Job.values())
            if (Normalizer.normalize(job.getLabel(lg).replace("{0}", ""), Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                    .toLowerCase().replaceAll("\\W+", "").startsWith(nameProposed))
                jobs.add(job.getName());
        return jobs;
    }

    @Override
    public String help(Language lg, String prefixe) {
        return "**" + prefixe + name + "** " + Translator.getLabel(lg, "job.help");
    }

    @Override
    public String helpDetailed(Language lg, String prefixe) {
        return help(lg, prefixe)
                + "\n" + prefixe + "`"  + name + "` : " + Translator.getLabel(lg, "job.help.detailed.1")
                + "\n" + prefixe + "`"  + name + " `*`métier`* : " + Translator.getLabel(lg, "job.help.detailed.2")
                + "\n" + prefixe + "`"  + name + " `*`métier niveau`* : " + Translator.getLabel(lg, "job.help.detailed.3")
                + "\n" + prefixe + "`"  + name + " -all `*`niveau`* : " + Translator.getLabel(lg, "job.help.detailed.4") + "\n";
    }
}
