package com.pixipanda;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PageViewGenerator {

    private static final int  MAX_FILE_LINES = 100;
    private static final int  N_COUNTRIES = 20;
    private static final int  MAX_IPS_PER_COUNTRY = 1000;
    static final int  SESSION_TIME_IN_SEC = 900;
    private static final int  MAX_CLICKS_PER_USER = 20;

    private int i, j, n_requests, n_referrers, n_user_agents, i_ctry=0, i_sum, r;
    private int[][] ipA_by_ctry = new int[MAX_IPS_PER_COUNTRY][N_COUNTRIES];
    private int[][] ipB_by_ctry = new int[MAX_IPS_PER_COUNTRY][N_COUNTRIES];
    private int[] tot_ips_by_ctry = new int[N_COUNTRIES];
    private int[] ctry_pct = new int[]{31, 13, 7, 5, 5, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 1, 1, 1};  // Top 20 countries
    private int[] hourly_weight = new int[]{4, 3, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 2, 2, 3, 13, 10, 8, 12, 12, 12, 10}; // Local time relative usage - consumer oriented website so peaks in evening
    private int[] ctry_time_diff = new int[]{13, 0, 11, 14, 2, 7, 9, 12, 6, 7, 6, 0, 14, 10, 8, 7, 13, 12, 7, 10}; // Relative to US Central, not worrying about DST
    private int[][] hourly_weight_by_ctry = new int[24][N_COUNTRIES];
    private int[][] cum_hourly_weight_by_ctry = new int[24][N_COUNTRIES];
    private int[] tot_weight_per_hour = new int[24];
    private int tot_weight_per_day=0;
    private int[] n_clicks_per_hour = new int[24];
    private int  i_record;
    private int i_hour, local_hour;
    private int[] status = new int[]{200,200,200,200,200,200,200,400,404,500};


    private String[] month_abbr = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private String[] ctry_abbr = new String[]{"CH", "US", "IN", "JP", "BR", "DE", "RU", "ID", "GB", "FR", "NG", "MX", "KR", "IR", "TR", "IT", "PH", "VN", "ES", "PK"};
    private String[] requests = new String[MAX_FILE_LINES];
    private String[] referrers = new String[MAX_FILE_LINES];
    private String[] user_agents = new String[MAX_FILE_LINES];

    private HashMap<String,String> ctry_ind = new HashMap<String, String>();

    private HashMap<String,String> ipCountryCode = new HashMap<String, String>();


    private String line;
    private String[] fields;


    public void intialize() throws IOException, InterruptedException {
        // Initialize arrays and read in data files
        for (i=0; i<N_COUNTRIES; i++)
        {
            tot_ips_by_ctry[i] = 0;
            ctry_ind.put(ctry_abbr[i], String.valueOf(i));
        }
        for (i=0; i< MAX_IPS_PER_COUNTRY; i++) {
            for (j=0; j<N_COUNTRIES; j++)
            {
                ipA_by_ctry[i][j] = 0;
                ipB_by_ctry[i][j] = 0;
            }
        }
        ipCountryCode.clear();
        initCountryIP();
        initRequest();
        initReferrers();
        initUserAgents();
        weight();
        initIpCountryCode();
    }



    public void initCountryIP() {
        BufferedReader br;
        try
        {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("all_classbs.txt").getFile());
            br =  new BufferedReader(new FileReader(file));  // Example line: 23.242 US
            while ((line = br.readLine()) != null)
            {
                //System.out.println("classb line: " + line);
                fields = line.split(" ");
                if (fields.length == 2)
                {
                    String i_ctry_s = ctry_ind.get(fields[1]);  // Look up ctry code in ctry_ind hash map, return ctry index (as string)
                    if (i_ctry_s != null)
                    {
                        i_ctry = Integer.parseInt(i_ctry_s);
                        String[] octets = fields[0].split("\\.");
                        if (tot_ips_by_ctry[i_ctry] < MAX_IPS_PER_COUNTRY)
                        {
                            ipA_by_ctry[tot_ips_by_ctry[i_ctry]][i_ctry] = Integer.parseInt(octets[0]);
                            ipB_by_ctry[tot_ips_by_ctry[i_ctry]][i_ctry] = Integer.parseInt(octets[1]);
                            ++tot_ips_by_ctry[i_ctry];
                        }
                        //System.out.println("Ab= " + fields[1] + " i_ctry= " + i_ctry +
                        //  " ipA=" + ipA_by_ctry[tot_ips_by_ctry[i_ctry]-1][i_ctry] +
                        //  " ipB=" + ipB_by_ctry[tot_ips_by_ctry[i_ctry]-1][i_ctry]);
                    }
                }
            }
            br.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        //for (i=0; i<N_COUNTRIES; i++)
        //  System.out.println("Number of class B IPS: " + ctry_abbr[i] + ": " + tot_ips_by_ctry[i]);
    }




    // Read in array of requests
    public void initRequest() {
        BufferedReader br;
        try
        {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("requests.txt").getFile());
            br =  new BufferedReader(new FileReader(file));
            i = 0;
            while ((line = br.readLine()) != null) requests[i++] = line;
            br.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        n_requests = i;
        //System.out.println("Lines read= " + n_requests);
        //for (i=0; i<n_requests; i++)
        //  System.out.println("Requests line " + i + ": " + requests[i]);
    }




    // Read in array of referrers
    public void initReferrers() {
        BufferedReader br;
        try
        {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("referrers.txt").getFile());
            br =  new BufferedReader(new FileReader(file));
            i = 0;
            while ((line = br.readLine()) != null) referrers[i++] = line;
            br.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        n_referrers = i;}
    //System.out.println("Lines read= " + n_referrers);
    //for (i=0; i<n_referrers; i++)
    //  System.out.println("referrers line " + i + ": " + referrers[i]);




    // Read in array of user agents
    public void initUserAgents() {
        BufferedReader br;
        try
        {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("user_agents.txt").getFile());
            br =  new BufferedReader(new FileReader(file));
            i = 0;
            while ((line = br.readLine()) != null) user_agents[i++] = line;
            br.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        n_user_agents = i;
        System.out.println("Lines read= " + n_user_agents);
        for (i=0; i<n_user_agents; i++)
            System.out.println("user_agents line " + i + ": " + user_agents[i]);
    }




    // Create table of weights by country and by hour, all relative to US Central
    private void weight() {
        for (i_hour=0; i_hour<24; i_hour++)
            for (i_ctry=0; i_ctry<N_COUNTRIES; i_ctry++)
            {
                local_hour = (i_hour + ctry_time_diff[i_ctry])%24;
                hourly_weight_by_ctry[i_hour][i_ctry] = hourly_weight[local_hour]*ctry_pct[i_ctry];
                //System.out.println("hourly_weight_by_ctry: " + hourly_weight_by_ctry[i_hour][i_ctry]);
            }
        for (i_hour=0; i_hour<24; i_hour++)
        {
            i_sum = 0;
            for (i_ctry=0; i_ctry<N_COUNTRIES; i_ctry++)
            {
                i_sum += hourly_weight_by_ctry[i_hour][i_ctry];
                cum_hourly_weight_by_ctry[i_hour][i_ctry] = i_sum;
                //System.out.println("cum_hourly_weight_by_ctry: " + cum_hourly_weight_by_ctry[i_hour][i_ctry]);
            }
            tot_weight_per_hour[i_hour] = i_sum;
            tot_weight_per_day += i_sum;
            //System.out.println("tot_weight per_day= " + tot_weight_per_day);
        }
    } // End setup

    private void initDateTime() {

    }



    private void  initIpCountryCode() {



        BufferedReader br;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("all_classbs.txt").getFile());
            br = new BufferedReader(new FileReader(file));  // Example line: 23.242 US
            while ((line = br.readLine()) != null) {
                //System.out.println("classb line: " + line);
                fields = line.split(" ");
                if (fields.length == 2) {

                    ipCountryCode.put(fields[0],fields[1]);
                }
            }br.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        for(Map.Entry<String, String> entry : ipCountryCode.entrySet()){
            System.out.println("key: " + entry.getKey() + " value: " + entry.getValue());

        }

    }


    @SuppressWarnings("deprecation")
    public void generatePageViews(Date fromDate, int n_records_per_day, String outputPath)  throws IOException, InterruptedException {

        //DataOutputStream out = new DataOutputStream(new	FileOutputStream(outputPath));
        FileWriter fstream = new FileWriter(outputPath, true);
        BufferedWriter out = new BufferedWriter(fstream);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");

        //String fn = String.format("access_log-%04d%02d%02d", year, month, day);


        for (i_hour=0; i_hour<24; i_hour++)
        {
            // Figure out number of clicks for each hour
            n_clicks_per_hour[i_hour] = Math.max(1, (int) Math.floor(0.5 + (double) n_records_per_day *
                    ((double) tot_weight_per_hour[i_hour]/(double) tot_weight_per_day)));
            for (i_ctry=0; i_ctry<N_COUNTRIES; i_ctry++)
                System.out.printf("hour=%d  ctry=%d  hour weight= %d  cum weight=%d\n", i_hour, i_ctry,
                        hourly_weight_by_ctry[i_hour][i_ctry], cum_hourly_weight_by_ctry[i_hour][i_ctry]);
            System.out.printf("tot_weight per_hour[%d]=%d n_clicks_per_hour[%d]=%d\n", i_hour, tot_weight_per_hour[i_hour], i_hour, n_clicks_per_hour[i_hour]);
        }





        //Main loop - generate log entry and write to log file

        Date workingDate = fromDate;
        int year = workingDate.getYear() + 1900;
        Calendar c = Calendar.getInstance();
        c.setTime(workingDate);
        int maxDays = c.getActualMaximum(Calendar.DAY_OF_YEAR);
        int month = workingDate.getMonth();
        int day = workingDate.getDate();

        // Set random seed equal to date, eg. 20120101
        System.out.println("day: " + day);
        Random rand = new Random(10000*year + 100*month + day);

        for (int iDay = 0; iDay < maxDays; iDay++) {

            double time_of_day_in_sec = 0.0;
            int hour = 0;
            int min = 0;
            int sec = 0;
            int clicks_left = 0;
            String ip4 = "";
            String referrer = "";
            String user_agent = "";
            System.out.println("Writing records for day: " + day + " month: " + month_abbr[month]);
            for (i_record = 0; i_record < n_records_per_day; i_record++) {

                double avg_time_between_clicks = (double) 3600 / n_clicks_per_hour[hour];
                //System.out.printf("hour=%d avg_time_between_clicks= %f\n", hour, avg_time_between_clicks);
                time_of_day_in_sec += rand.nextDouble() * 2 * avg_time_between_clicks;
                hour = (int) Math.floor(time_of_day_in_sec / 3600);
                min = (int) Math.floor((time_of_day_in_sec - hour * 3600) / 60);
                sec = (int) time_of_day_in_sec - hour * 3600 - min * 60;
                if (hour > 23) {
                    hour = 23;
                    min = 59;
                    sec = 59;
                }
                if (min > 59) min = 59;
                if (sec > 59) sec = 59;
                String timestamp = String.format("%02d:%02d:%02d", hour, min, sec);

                {
                    // Pick random number for given hour, then look up country in cum weights, then pick random row for IP
                    r = 1 + rand.nextInt(tot_weight_per_hour[hour]);
                    i_ctry = 0;
                    while (r > cum_hourly_weight_by_ctry[hour][i_ctry]) ++i_ctry;
                    //System.out.printf("hour=%d r=%d i_ctry=%d\n", hour, r, i_ctry);
                    i = rand.nextInt(tot_ips_by_ctry[i_ctry]);
                    ip4 = String.format("%d.%d.%d.%d", ipA_by_ctry[i][i_ctry],
                            ipB_by_ctry[i][i_ctry], 2 + rand.nextInt(249), 2 + rand.nextInt(249));
                    clicks_left = 1 + rand.nextInt(MAX_CLICKS_PER_USER);
                    referrer = referrers[rand.nextInt(n_referrers)];
                    user_agent = user_agents[rand.nextInt(n_user_agents)];
                }


                String[] ipOctets = ip4.split("\\.");

                String ipKey = ipOctets[0] + "." + ipOctets[1];

                String countryCode = ipCountryCode.get(ipKey);

                String output = String.format("%s - %d [%02d/%3s/%4d:%8s -0500] \"%s\" %d %d \"%s\" \"%s\" \"%s\"\n",
                        ip4,
                        i,
                        day,
                        month_abbr[month],
                        year,
                        timestamp,
                        requests[rand.nextInt(n_requests)],
                        status[rand.nextInt(10)],
                        rand.nextInt(4096),
                        referrer,
                        user_agent,
                        countryCode);

                String csvoutput = String.format("%s,-,%d,%02d/%3s/%4d:%8s -0500,\"%s\",%d,%d,\"%s\",\"%s\",\"%s\",\n",
                        ip4,
                        i,
                        day,
                        month_abbr[month],
                        year,
                        timestamp,
                        requests[rand.nextInt(n_requests)],
                        status[rand.nextInt(10)],
                        rand.nextInt(4096),
                        referrer,
                        user_agent,
                        countryCode);
                out.write(csvoutput);
                //System.out.print(output);
            }
            c.add(Calendar.DATE, 1);
            workingDate = c.getTime();
            day = workingDate.getDate();
            month = workingDate.getMonth();
        }
    } //pageViewGenerator




    public static void main(String[] args) throws ParseException, InterruptedException, IOException {

        int n_records_per_day = Integer.parseInt(args[0]);
        String output = args[1];
        String fromDateString = args[2];
        //String toDateString = args[3];

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy");
        try {
            Date fromdate = dateFormat.parse(fromDateString);
            Calendar c = Calendar.getInstance();
            c.setTime(fromdate);
            c.add(Calendar.YEAR, 1);
            Date toDate = c.getTime();
            System.out.println(dateFormat.format(toDate));
            PageViewGenerator gen = new PageViewGenerator();
            gen.intialize();
            gen.generatePageViews(fromdate, n_records_per_day, output);
        }
        catch (ParseException e) {
            System.out.print("Invalid date");
        }

    }
}
