/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.izykube.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gitops")
public class GitOpsProperties {

    private boolean enabled = true;
    private String repoUrl = "http://gitea-http.gitea.svc.cluster.local:3000/izykube/izykube-gitops.git";
    private String argoCdRepoUrl = "";
    private String branch = "main";
    private String basePath = "apps";
    private String username = "";
    private String password = "";
    private String commitAuthorName = "IzyKube";
    private String commitAuthorEmail = "noreply@izylife.local";
    private String argoCdNamespace = "argocd";
    private String argoCdProject = "default";
    private boolean autoSync = true;
    private boolean prune = true;
    private boolean selfHeal = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getBranch() {
        return branch;
    }

    public String getArgoCdRepoUrl() {
        return argoCdRepoUrl;
    }

    public void setArgoCdRepoUrl(String argoCdRepoUrl) {
        this.argoCdRepoUrl = argoCdRepoUrl;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCommitAuthorName() {
        return commitAuthorName;
    }

    public void setCommitAuthorName(String commitAuthorName) {
        this.commitAuthorName = commitAuthorName;
    }

    public String getCommitAuthorEmail() {
        return commitAuthorEmail;
    }

    public void setCommitAuthorEmail(String commitAuthorEmail) {
        this.commitAuthorEmail = commitAuthorEmail;
    }

    public String getArgoCdNamespace() {
        return argoCdNamespace;
    }

    public void setArgoCdNamespace(String argoCdNamespace) {
        this.argoCdNamespace = argoCdNamespace;
    }

    public String getArgoCdProject() {
        return argoCdProject;
    }

    public void setArgoCdProject(String argoCdProject) {
        this.argoCdProject = argoCdProject;
    }

    public boolean isAutoSync() {
        return autoSync;
    }

    public void setAutoSync(boolean autoSync) {
        this.autoSync = autoSync;
    }

    public boolean isPrune() {
        return prune;
    }

    public void setPrune(boolean prune) {
        this.prune = prune;
    }

    public boolean isSelfHeal() {
        return selfHeal;
    }

    public void setSelfHeal(boolean selfHeal) {
        this.selfHeal = selfHeal;
    }
}

