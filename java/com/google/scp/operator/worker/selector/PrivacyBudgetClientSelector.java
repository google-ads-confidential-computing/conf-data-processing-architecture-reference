package com.google.scp.operator.worker.selector;

import com.google.inject.Module;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.aws.AwsPbsClientModule;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.gcp.GcpPbsClientModule;
import com.google.scp.operator.cpio.distributedprivacybudgetclient.local.LocalDistributedPrivacyBudgetClientModule;

/** CLI enum to select the privacy budget client implementation */
public enum PrivacyBudgetClientSelector {
  /** Selector for the local client */
  LOCAL(new LocalDistributedPrivacyBudgetClientModule()),
  /** Selector for Distributed Privacy Budget Client on AWS */
  AWS(new AwsPbsClientModule()),
  /** Selector for Distributed Privacy Budget Client on GCP */
  GCP(new GcpPbsClientModule());

  private final Module distributedPrivacyBudgetClientModule;

  PrivacyBudgetClientSelector(Module module) {
    this.distributedPrivacyBudgetClientModule = module;
  }

  public Module getDistributedPrivacyBudgetClientModule() {
    return distributedPrivacyBudgetClientModule;
  }
}
