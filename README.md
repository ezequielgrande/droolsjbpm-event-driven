droolsjbpm-event-driven
=======================

The purpose of this Project is to show different ways of connecting Business Rules and Processes, based on Complex Event Processing (Drools Fusion).

##Project description
This project represents the monitoring of Servers among different Locations. There are business rules that monitor the status of the servers and there is a Business Process in order to receive claims from customers when a server is not working as expected.

##Project resources
* **Business Process:** A simple business process which has 3 tasks:
  *  Receive a claim (Human Task): An operator will receive a claim from a customer.
  *  Technical inspection (Human Task): A technical inspector will inspect the problem and update the claim status.
  *  Print status (Script task): A script that prints in the standard output the value of process variables.
*  **Business Rules:**
  * A warning will be created when there are more than 100+ uncompleted process within the last hour.
  * Events will be generated when a Server changes its status (ON/OFF).
  * Detect Location outage: this rule will detect when all the servers of a Location has been shut down.
  * Start new Process when there is a Location outage: this rule will start a new Process instance when there is a Location Outage detected. This new Process instance will be awaiting in the first Human Task, so operators will be able to work on it. 
  * Detect failing server: when a server has been turned on/off at least 5 times in the last 10 minutes.
* **JUnit Test Cases**
  * Validates that the Business Process and the Business Rules are working as expected

##Next steps / Wishlist
* **Demo with UI:** Be able to turn on/off different servers from an UI (text console / graphical interface) and see the system behavior in real time.
* **Temporal Operators:** Add more operators to the example (read more in [drools-docs](http://docs.jboss.org/drools/release/6.1.0.Final/drools-docs/html_single/index.html#d0e10576))
* **Business Process**: Monitor the performance of the operators and automatically assign tasks based on the current work load of each operator/technical inspector.
