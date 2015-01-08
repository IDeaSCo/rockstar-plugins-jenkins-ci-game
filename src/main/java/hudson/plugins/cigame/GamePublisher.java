package hudson.plugins.cigame;

import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.cigame.model.RuleBook;
import hudson.plugins.cigame.model.Score;
import hudson.plugins.cigame.model.ScoreCard;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import java.io.IOException;
import java.util.*;

public class GamePublisher extends Notifier {

    AbstractBuild upstreamBuild = null;
    @Override
    public GameDescriptor getDescriptor() {
        return (GameDescriptor) super.getDescriptor();
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) throws InterruptedException, IOException {

        perform(build, getDescriptor().getRuleBook(), getDescriptor().getNamesAreCaseSensitive(), listener, getDescriptor().getIdeasRockStarURI(), getDescriptor().getIdeasRockStarEmail());
        return true;
    }

    /**
     * Calculates score from the build and rule book and adds a Game action to the build.
     * @param build build to calculate points for
     * @param ruleBook rules used in calculation
     * @param usernameIsCasesensitive user names in Hudson are case insensitive.
     * @param listener the build listener
     * @return true, if any user scores were updated; false, otherwise
     * @throws IOException thrown if there was a problem setting a user property
     */
    boolean perform(AbstractBuild<?, ?> build, RuleBook ruleBook, boolean usernameIsCasesensitive, BuildListener listener, String ideasRockStarURI, String ideasRockStarEmail) throws IOException {
        ScoreCard sc = new ScoreCard();
        sc.record(build, ruleBook, listener);

        ScoreCardAction action = new ScoreCardAction(sc, build);
        build.getActions().add(action);

        List<AbstractBuild<?, ?>> accountableBuilds = new ArrayList<AbstractBuild<?,?>>();
        accountableBuilds.add(build);

        upstreamBuild = getUpstreamByCause(build, listener);
        if(upstreamBuild!= null) {
            accountableBuilds.add(upstreamBuild);
            ChangeLogSet<? extends Entry> changeSet = upstreamBuild.getChangeSet();
            if(listener != null ) listener.getLogger().append("[ci-game] UpStream Build ID: " + upstreamBuild.getId()+ "\n");
            if(listener != null ) listener.getLogger().append("[ci-game] UpStream Display Name: " + upstreamBuild.getFullDisplayName()+ "\n");
            if(listener != null ) listener.getLogger().append("[ci-game] Is UpStream Change Set Empty: " + changeSet.isEmptySet() + "\n");

        }

        // also add all previous aborted builds:
        AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
        while (previousBuild != null && previousBuild.getResult() == Result.ABORTED) {
            if(listener != null ) listener.getLogger().append("[ci-game] Previous Build ID: " + previousBuild.getId()+ "\n");
            if(listener != null ) listener.getLogger().append("[ci-game] Previous Display Name: " + previousBuild.getFullDisplayName()+ "\n");

            accountableBuilds.add(previousBuild);
            previousBuild = previousBuild.getPreviousBuild();
        }

        Set<User> players = new TreeSet<User>(usernameIsCasesensitive ? null : new UsernameCaseinsensitiveComparator());
        for (AbstractBuild<?, ?> b : accountableBuilds) {
            ChangeLogSet<? extends Entry> changeSet = b.getChangeSet();
            if (changeSet != null) {
                for (Entry e : changeSet) {
                    players.add(e.getAuthor());
                }
            }
        }
        sendStarScore(build,players, sc , listener,ideasRockStarURI, ideasRockStarEmail);
        return updateUserScores(players, sc.getTotalPoints(), accountableBuilds, listener);
    }

    private static AbstractBuild getBuildByUpstreamCause(List<Cause> causes,BuildListener listener ){
        for(Cause cause: (List<Cause>) causes){
            if(cause instanceof Cause.UpstreamCause) {
                TopLevelItem upstreamProject = Hudson.getInstance().getItemByFullName(((Cause.UpstreamCause)cause).getUpstreamProject(), TopLevelItem.class);
                if(upstreamProject instanceof AbstractProject){
                    int buildId = ((Cause.UpstreamCause)cause).getUpstreamBuild();
                    Run run = ((AbstractProject) upstreamProject).getBuildByNumber(buildId);
                    System.out.println();
                    AbstractBuild upstreamRun = getBuildByUpstreamCause(run.getCauses(),listener);
                    if(upstreamRun == null) {
                        return (AbstractBuild) run;
                    }else{
                        return upstreamRun;
                    }
                }
            }
        }
        return null;

    }
    private static AbstractBuild getUpstreamByCause(AbstractBuild build, BuildListener listener) {
        return getBuildByUpstreamCause(build.getCauses(),listener);
    }

    private void sendStarScore(AbstractBuild<?, ?>  build, Set<User> players, ScoreCard sc, BuildListener listener, String ideasRockStarURI, String ideasRockStarEmail){
        if (sc.getTotalPoints() != 0) {

                try {
                    Collection<Score> scores = sc.getScores();
                    for(Score score:scores){
                        if(upstreamBuild != null && score.getDescription().equals("The build was successful")){
                            continue;
                        }

                        new GalaxyUpdater(ideasRockStarURI, ideasRockStarEmail).update(players,score.getValue(),"Build:"+build.getFullDisplayName()+":"+score.getDescription(), listener, score.getBadge());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

        }

    }
    /**
     * Add the score to the users that have committed code in the change set
     *
     *
     * @param score the score that the build was worth
     * @param accountableBuilds the builds for which the {@code score} is awarded for.
     * @throws IOException thrown if the property could not be added to the user object.
     * @return true, if any user scores was updated; false, otherwise
     */

    private boolean updateUserScores(Set<User> players, double score, List<AbstractBuild<?, ?>> accountableBuilds, BuildListener listener) throws IOException {
        if (score != 0) {
            for (User user : players) {
                UserScoreProperty property = user.getProperty(UserScoreProperty.class);
                if (property == null) {
                    property = new UserScoreProperty();
                    user.addProperty(property);
                }
                if (property.isParticipatingInGame()) {
                    property.setScore(property.getScore() + score);
                    property.rememberAccountableBuilds(accountableBuilds, score);
                }
                user.save();
            }
        }
        return (!players.isEmpty());
    }

    public static class UsernameCaseinsensitiveComparator implements Comparator<User> {
        public int compare(User arg0, User arg1) {
            return arg0.getId().compareToIgnoreCase(arg1.getId());
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }
}
