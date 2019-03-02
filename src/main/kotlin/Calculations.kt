import koma.all
import koma.create
import koma.extensions.get
import koma.matrix.Matrix
import java.util.*

// adapted from https://blog.thebluealliance.com/2017/10/05/the-math-behind-opr-an-introduction/
object Calculations {
    var numbers: MutableSet<String>? = null

    /**
     * Gives a set of team numbers present in a list of alliances (no duplicates)
     */
    fun getTeamNumbers(alliances: ArrayList<Alliance>){
        numbers = mutableSetOf<String>()
        alliances.forEach {
            numbers?.addAll(arrayOf(it.team1, it.team2, it.team3))
        }
    }

    /**
     * Generates a, b, and c matrices for a list of alliances
     */
    fun createMatchMatrices(alliances: ArrayList<Alliance>): Triple<Matrix<Double>, Matrix<Double>, Matrix<Double>> {
        getTeamNumbers(alliances)
        val a = arrayListOf<DoubleArray>()
        val b = arrayListOf<Double>()
        val c = arrayListOf<Double>()
        alliances.forEach {
            a.add(createMatchMatrix(arrayOf(it.team1, it.team2, it.team3)))
            b.add(it.hatchScore.toDouble())
            c.add(it.cargoScore.toDouble())
        }

        val m_a = create(a.toTypedArray())
        val m_b = create(b.toDoubleArray(), b.size, 1)
        val m_c = create(c.toDoubleArray(), c.size, 1)

        return Triple(m_a, m_b, m_c)
    }

    /**
     * Creates the one-hot team matrix for a match
     */
    fun createMatchMatrix(teams: Array<String>): DoubleArray {
        val line = DoubleArray(numbers!!.size) {
            if(teams.contains(numbers!!.elementAt(it))) 1.0 else 0.0
        }
//        println(Arrays.toString(line))
        return line
    }

    /**
     * Solves the system of alliance equations for panels and cargo
     */
    fun calculatePieceScores(matrices: Triple<Matrix<Double>, Matrix<Double>, Matrix<Double>>): Pair<Matrix<Double>, Matrix<Double>> {
        val mul_a = matrices.first.T * matrices.first
        val mul_b = matrices.first.T * matrices.second
        val mul_c = matrices.first.T * matrices.third

        val mul_a_inv = mul_a.pinv()

        return Pair(mul_a_inv * mul_b,mul_a_inv * mul_c)
    }

    /**
     * Maps game piece matrices into a map with teams
     */
    fun mapPieceScoresToTeams(pieces: Pair<Matrix<Double>, Matrix<Double>>): Pair<MutableMap<String, Double>, MutableMap<String, Double>> {
        val m_b = mutableMapOf<String, Double>()
        val m_c = mutableMapOf<String, Double>()

        numbers?.indices?.forEach {
            m_b[numbers!!.elementAt(it)] = pieces.first[it]
            m_c[numbers!!.elementAt(it)] = pieces.second[it]
        }

        return Pair(m_b, m_c)
    }
}