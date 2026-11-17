package com.example.genesequenceanalyser

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import biokt.DNASequence
import biokt.Orf
import biokt.SeqIO
import com.example.genesequenceanalyser.adapter.HistoryAdapter
import com.example.genesequenceanalyser.adapter.OrfAdapter
import com.example.genesequenceanalyser.db.AppDatabase
import com.example.genesequenceanalyser.db.SequenceEntity
import com.example.genesequenceanalyser.network.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogin: Button
    private lateinit var etAccession: EditText
    private lateinit var btnScan: Button
    private lateinit var btnDownload: Button
    private lateinit var progressBar: ProgressBar

    // Analysis Views
    private lateinit var cardAnalysis: View
    private lateinit var tvAccessionHeader: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvSeqLength: TextView
    private lateinit var tvGcContent: TextView
    private lateinit var tvMeltingTemp: TextView
    private lateinit var tvBaseCounts: TextView
    private lateinit var tvRevComp: TextView
    private lateinit var tvProtein: TextView
    private lateinit var btnSaveHistory: Button

    // ORFs Views
    private lateinit var rvOrfs: RecyclerView
    private lateinit var tvEmptyOrf: TextView
    private lateinit var orfAdapter: OrfAdapter

    // Room History Views
    private lateinit var etSearchHistory: EditText
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmptyHistory: TextView
    private lateinit var historyAdapter: HistoryAdapter

    // Firebase & Google Auth
    private var auth: FirebaseAuth? = null
    private var googleSignInClient: GoogleSignInClient? = null

    // Database & Current Analysis State
    private lateinit var database: AppDatabase
    private var currentAccession: String = ""
    private var currentDescription: String = ""
    private var currentLength: Int = 0
    private var currentGcPercentage: Double = 0.0
    private var currentMeltingTemp: Double = 0.0

    // Google Sign-In Launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { firebaseAuthWithGoogle(it) }
        } catch (e: Exception) {
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Image Picker / Camera Launcher for OCR
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processImageForOcr(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupFirebaseAndGoogleAuth()
        setupRoomDatabase()
        setupRecyclerViews()
        setupListeners()
    }

    private fun initViews() {
        tvUserEmail = findViewById(R.id.tvUserEmail)
        btnLogin = findViewById(R.id.btnLogin)
        etAccession = findViewById(R.id.etAccession)
        btnScan = findViewById(R.id.btnScan)
        btnDownload = findViewById(R.id.btnDownload)
        progressBar = findViewById(R.id.progressBar)

        cardAnalysis = findViewById(R.id.cardAnalysis)
        tvAccessionHeader = findViewById(R.id.tvAccessionHeader)
        tvDescription = findViewById(R.id.tvDescription)
        tvSeqLength = findViewById(R.id.tvSeqLength)
        tvGcContent = findViewById(R.id.tvGcContent)
        tvMeltingTemp = findViewById(R.id.tvMeltingTemp)
        tvBaseCounts = findViewById(R.id.tvBaseCounts)
        tvRevComp = findViewById(R.id.tvRevComp)
        tvProtein = findViewById(R.id.tvProtein)
        btnSaveHistory = findViewById(R.id.btnSaveHistory)

        rvOrfs = findViewById(R.id.rvOrfs)
        tvEmptyOrf = findViewById(R.id.tvEmptyOrf)

        etSearchHistory = findViewById(R.id.etSearchHistory)
        rvHistory = findViewById(R.id.rvHistory)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)
    }

    private fun setupFirebaseAndGoogleAuth() {
        try {
            auth = FirebaseAuth.getInstance()
            val webClientId = getString(R.string.default_web_client_id)
            if (webClientId.isNotEmpty() && !webClientId.startsWith("YOUR_FIREBASE")) {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()
                googleSignInClient = GoogleSignIn.getClient(this, gso)
            }
            updateAuthUI()
        } catch (e: Exception) {
            e.printStackTrace()
            tvUserEmail.text = getString(R.string.not_logged_in)
        }
    }

    private fun updateAuthUI() {
        try {
            val user = auth?.currentUser
            if (user != null) {
                tvUserEmail.text = "Logged in as: ${user.email}"
                btnLogin.text = getString(R.string.sign_out)
            } else {
                tvUserEmail.text = getString(R.string.not_logged_in)
                btnLogin.text = getString(R.string.google_sign_in)
            }
        } catch (e: Exception) {
            tvUserEmail.text = getString(R.string.not_logged_in)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth?.signInWithCredential(credential)
            ?.addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    updateAuthUI()
                } else {
                    Toast.makeText(this, "Firebase Auth failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupRoomDatabase() {
        try {
            database = AppDatabase.getDatabase(this)
            database.sequenceDao().getAllHistory().observe(this) { historyList ->
                if (etSearchHistory.text.isNullOrEmpty()) {
                    updateHistoryUI(historyList)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupRecyclerViews() {
        orfAdapter = OrfAdapter()
        rvOrfs.layoutManager = LinearLayoutManager(this)
        rvOrfs.adapter = orfAdapter

        historyAdapter = HistoryAdapter { entityToDelete ->
            lifecycleScope.launch(Dispatchers.IO) {
                database.sequenceDao().delete(entityToDelete)
            }
        }
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            try {
                if (auth?.currentUser != null) {
                    auth?.signOut()
                    googleSignInClient?.signOut()
                    updateAuthUI()
                    Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                } else if (googleSignInClient != null) {
                    val signInIntent = googleSignInClient!!.signInIntent
                    googleSignInLauncher.launch(signInIntent)
                } else {
                    Toast.makeText(this, "Google Auth requires valid google-services.json configuration.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Auth Exception: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnScan.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        btnDownload.setOnClickListener {
            val accession = etAccession.text.toString().trim()
            if (accession.isEmpty()) {
                Toast.makeText(this, "Please enter an Accession Number", Toast.LENGTH_SHORT).show()
            } else {
                downloadAndAnalyzeFasta(accession)
            }
        }

        btnSaveHistory.setOnClickListener {
            saveCurrentAnalysisToHistory()
        }

        etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    database.sequenceDao().getAllHistory().observe(this@MainActivity) { list ->
                        updateHistoryUI(list)
                    }
                } else {
                    database.sequenceDao().searchHistory("%$query%").observe(this@MainActivity) { list ->
                        updateHistoryUI(list)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun processImageForOcr(imageUri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            progressBar.visibility = View.VISIBLE
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    progressBar.visibility = View.GONE
                    val extractedText = visionText.text
                    val accession = extractAccessionNumber(extractedText)
                    if (accession != null) {
                        etAccession.setText(accession)
                        Toast.makeText(this, "Extracted Accession: $accession", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Could not find valid Accession Number in image. Please enter manually.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "OCR Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Image Loading Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAccessionNumber(rawText: String): String? {
        val sanitized = rawText.uppercase()
            .replace(" ", "")
            .replace("\n", "")

        val pattern = Pattern.compile("([A-Z]{1,2}_?\\d{5,9}(\\.\\d+)?)")
        val matcher = pattern.matcher(sanitized)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    private fun downloadAndAnalyzeFasta(accession: String) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getFastaSequence(accession = accession)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                        val fastaText = response.body()!!
                        parseAndAnalyzeBioKt(accession, fastaText)
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to download sequence. Check accession ID.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseAndAnalyzeBioKt(accession: String, fastaText: String) {
        try {
            val records = SeqIO.parseString(fastaText, "fasta")
            if (records.isEmpty()) {
                Toast.makeText(this, "No sequence records found in response", Toast.LENGTH_SHORT).show()
                return
            }

            val record = records.first()
            val sequenceStr = record.sequence.sequence
            val descriptionStr = if (record.description.isNotEmpty()) record.description else record.id

            val dna = DNASequence(sequenceStr, id = accession, description = descriptionStr)

            currentAccession = accession
            currentDescription = descriptionStr
            currentLength = dna.length
            currentGcPercentage = dna.gcContent()
            currentMeltingTemp = dna.meltingTemperature()

            tvAccessionHeader.text = "Accession: $accession"
            tvDescription.text = "Description: $descriptionStr"
            tvSeqLength.text = "Length: ${dna.length} bp"
            tvGcContent.text = "GC Content: ${String.format(Locale.US, "%.2f", currentGcPercentage)}%"
            tvMeltingTemp.text = "Melting Temp (Tm): ${String.format(Locale.US, "%.1f", currentMeltingTemp)} °C"

            val counts = dna.countAll()
            val aCount = counts['A'] ?: 0
            val tCount = counts['T'] ?: 0
            val gCount = counts['G'] ?: 0
            val cCount = counts['C'] ?: 0
            tvBaseCounts.text = "Bases: A:$aCount T:$tCount G:$gCount C:$cCount"

            val revComp = dna.reverseComplement().sequence
            tvRevComp.text = revComp

            val protein = dna.translate().sequence
            tvProtein.text = protein

            val orfs: List<Orf> = dna.findOrfs(minLength = 100)
            if (orfs.isEmpty()) {
                tvEmptyOrf.visibility = View.VISIBLE
                rvOrfs.visibility = View.GONE
            } else {
                tvEmptyOrf.visibility = View.GONE
                rvOrfs.visibility = View.VISIBLE
                orfAdapter.updateData(orfs)
            }

            cardAnalysis.visibility = View.VISIBLE
            Toast.makeText(this, "Analysis Completed Successfully!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "bioKT Analysis Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveCurrentAnalysisToHistory() {
        if (currentAccession.isEmpty()) {
            Toast.makeText(this, "No analyzed sequence to save", Toast.LENGTH_SHORT).show()
            return
        }

        val entity = SequenceEntity(
            accessionId = currentAccession,
            description = currentDescription,
            sequenceLength = currentLength,
            gcPercentage = currentGcPercentage,
            meltingTemp = currentMeltingTemp
        )

        lifecycleScope.launch(Dispatchers.IO) {
            database.sequenceDao().insert(entity)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Saved to History!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateHistoryUI(list: List<SequenceEntity>) {
        if (list.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
        } else {
            tvEmptyHistory.visibility = View.GONE
            rvHistory.visibility = View.VISIBLE
            historyAdapter.updateData(list)
        }
    }
}