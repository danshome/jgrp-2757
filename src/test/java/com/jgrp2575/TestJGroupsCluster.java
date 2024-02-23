package com.jgrp2575;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestJGroupsCluster {

  private static final Logger logger = LoggerFactory.getLogger(TestJGroupsCluster.class);
  private final List<ApplicationThread> applicationThreads = new ArrayList<>();
  int numberOfApplications = 42;

  @BeforeEach
  public void setUp() {
    System.setProperty("jgroups.bind_addr", "localhost");
    for (int applicationId = 1; applicationId <= numberOfApplications; applicationId++) {
      ApplicationThread applicationThread = new ApplicationThread(applicationId);
      Thread t = new Thread(applicationThread);
      applicationThreads.add(applicationThread);
      t.start();
    }
  }

  @AfterEach
  public void tearDown() {
    System.clearProperty("jgroups.bind_addr");
    // Close the channel when done
    logger.info("SHUTTING DOWN....");
    applicationThreads.forEach(ApplicationThread::setShutdown);
    try {
      Thread.sleep(10000);
    } catch (InterruptedException ignored) {
    }
  }

  @Test
  public void testClusterFormation() throws Exception {
    // Wait for 30 minutes for cluster to form
    Thread.sleep(1000 * 60 * 5);
  }

  private static void randomSleep(int minMillis, int maxMillis) {
    try {
      Random random = new Random();
      int randomMillis = random.nextInt(maxMillis - minMillis + 1) + minMillis;
      Thread.sleep(randomMillis);
    } catch (InterruptedException e) {
    }
  }

  public static class ChannelReceiver implements Receiver {
    String name;

    ChannelReceiver(String name) {
      this.name = name;
    }

    @Override
    public void receive(Message msg) {
      // Handle incoming messages
      logger.info(name + " Received message: " + msg.getObject());
    }

    @Override
    public void viewAccepted(View view) {
      // Handle view changes (membership changes)
      logger.info(
          name
              + ": "
              + new Date()
              + " : View Member Count: "
              + (view.getMembers().isEmpty() ? "no members" : view.getMembers().size()));
    }
  }

  private class ApplicationThread implements Runnable {
    private final int applicationId;
    private boolean shutdown = false;

    public ApplicationThread(int applicationId) {
      this.applicationId = applicationId;
    }

    public void setShutdown() {
      shutdown = true;
    }

    @Override
    public void run() {

      try {
        randomSleep(1000, 30000);
        JChannel channel =
            new JChannel(getClass().getResourceAsStream("/profiles/local/jgroups_config.xml"));

        // Set a receiver to handle incoming messages and view changes
        channel.setName("CLUSTER_NODE_" + applicationId);
        channel.setReceiver(new ChannelReceiver(channel.getName()));
        // Connect to the cluster
        channel.connect("TestCluster"); // "TestCluster" is the name of the test cluster
        while (!shutdown) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            shutdown = true;
            break;
          }
        }
        channel.close();
        logger.info("{} Shutdown", channel.getName());
      } catch (Throwable t) {
        logger.error("An error occurred", t);
      }
    }
  }
}
