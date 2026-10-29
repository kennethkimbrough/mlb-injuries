<h1>MLB Injury Tracker and Game Predictor</h1>

<h2>Overview</h2>

<p>This program is designed to provide a comprehensive view of active injured MLB players, along with additional features to enhance the user's experience. The program utilizes the SportsData.io API to fetch data on injured players and will eventually include features such as:</p>

<ul>
  <li>Scheduled games with pitching matchups</li>
  <li>Algorithmic calculation of which team will win</li>
  <li>Algorithmic calculation of total runs</li>
  <li>Projected home run hitters for the day</li>
  <li>And more...</li>
</ul>

<h2>Current Features</h2>

<ul>
  <li>Lists all active injured MLB players, including their name, team, position, and injury status</li>
</ul>

<h2>Technical Details</h2>

<ul>
  <li>Written in Java</li>
  <li>Utilizes the Apache HTTP Client library for API requests</li>
  <li>Parses JSON responses using the Jackson library</li>
  <li>Currently uses the SportsData.io API for data retrieval</li>
</ul>

<h2>Code Structure</h2>

<p>The program consists of a single class, <code>MLBTeamStatsAPI</code>, which contains the main method. This class is responsible for:</p>

<ul>
  <li>Sending an HTTP GET request to the SportsData.io API to retrieve injured player data</li>
  <li>Parsing the JSON response using the Jackson library</li>
  <li>Extracting relevant data from the JSON response and printing it to the console</li>
</ul>

<h2>Future Development</h2>

<p>The program is currently in its early stages, and several features are planned for future development. These include:</p>

<ul>
  <li>Integrating additional APIs to retrieve data on scheduled games, pitching matchups, and team statistics</li>
  <li>Implementing algorithmic calculations for game predictions and total runs</li>
  <li>Developing a user-friendly interface for displaying data and predictions</li>
</ul>

<h2>Contributing</h2>

<p>If you're interested in contributing to this project, please feel free to submit a pull request or open an issue to discuss potential features or improvements.</p>

<h2>License</h2>

<p>This program is licensed under the MIT License. See the <code>LICENSE</code> file for details.</p>

<h2>Acknowledgments</h2>

<p>This program utilizes the SportsData.io API for data retrieval. Please note that an API key is required to use this program: set it in the <code>SPORTSDATA_API_KEY</code> environment variable before running.</p>
